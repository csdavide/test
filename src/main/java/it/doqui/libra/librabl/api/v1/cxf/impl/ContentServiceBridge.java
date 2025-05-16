package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.cxf.mappers.ContentMapper;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.exceptions.AnalysisException;
import it.doqui.libra.librabl.business.service.interfaces.DocumentAnalyzer;
import it.doqui.libra.librabl.business.service.interfaces.NodeContentService;
import it.doqui.libra.librabl.business.service.interfaces.NodeService;
import it.doqui.libra.librabl.business.service.interfaces.TemporaryService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.*;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.*;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;

@ApplicationScoped
@Slf4j
public class ContentServiceBridge extends AbstractServiceBridge {

    @Inject
    NodeService nodeService;

    @Inject
    NodeContentService nodeContentService;

    @Inject
    DocumentAnalyzer documentAnalyzer;

    @Inject
    ContentMapper contentMapper;

    @Inject
    TemporaryService temporaryService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Attachment downloadMethod(String uid, String usr, String pwd, String repo, String prefixedName) {
        validate(() -> {
            Objects.requireNonNull(uid, "UUID must not be null");
            Objects.requireNonNull(usr, "username must not be null");
            Objects.requireNonNull(pwd, "password must not be null");
            Objects.requireNonNull(repo, "repository must not be null");
            Objects.requireNonNull(prefixedName, "content property prefixed name must not be null");

            PrefixedQName qname = PrefixedQName.valueOf(prefixedName);
            if (StringUtils.isBlank(qname.getNamespaceURI()) || StringUtils.isBlank(qname.getLocalPart())) {
                throw new InvalidParameterException("Invalid content property name: " + prefixedName);
            }
        });

        MtomOperationContext context = new MtomOperationContext();
        context.setUsername(usr);
        context.setPassword(pwd);
        context.setRepository(repo);
        context.setFruitore(usr);
        context.setNomeFisico(usr);

        return call(context, () -> {
            NodeAttachment a = nodeContentService.getNodeContent(uid, prefixedName);
            if (!StringUtils.equals(a.getContentProperty().getName(), prefixedName)) {
                throw new InvalidParameterException(
                    String.format("Provided content property name '%s' doesn't match with '%s'", prefixedName, a.getContentProperty().getName()));
            }

            String mimeType = a.getContentProperty().getMimetype();
            try {
                MediaType.valueOf(a.getContentProperty().getMimetype());
            } catch (Exception e) {
                mimeType = MediaType.APPLICATION_OCTET_STREAM;
            }

            File f = a.getFile();
            Attachment attachment = new Attachment();
            attachment.fileName = PrefixedQName.valueOf(a.getName()).getLocalPart();
            attachment.fileSize = f.length();
            attachment.fileType = mimeType;
            attachment.attachmentDataHandler = new DataHandler(new FileDataSource(f));
            return attachment;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public byte[] retrieveContentData(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(node);

        String cname = content.getContentPropertyPrefixedName();
        validate(() -> {
            Objects.requireNonNull(content, "Content node must not be null");
            Objects.requireNonNull(cname, "Content property name must not be null");

            PrefixedQName qname = PrefixedQName.valueOf(cname);
            if (StringUtils.isBlank(qname.getNamespaceURI()) || StringUtils.isBlank(qname.getLocalPart())) {
                throw new InvalidParameterException("Invalid content property name: " + cname);
            }
        });

        return call(context, () -> {
            NodeAttachment a = nodeContentService.getNodeContent(node.getUid(), cname);
            try (FileInputStream is = new FileInputStream(a.getFile())) {
                return is.readAllBytes();
            }
        });
    }

    public File[] massiveRetrieveFiles(Node[] nodes, Content[] contents, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        LinkedHashMap<String, ContentRequest> inputs = new LinkedHashMap<>();
        validate(() -> {
            Objects.requireNonNull(nodes, "Node must not be null");
            Objects.requireNonNull(contents, "Contents must not be null");
            if (nodes.length != contents.length) {
                throw new InvalidParameterException(
                    String.format("Nodes and Contents must have the same size. #nodes = %d, #newContents = %d", nodes.length, contents.length));
            }

            for (int i = 0; i < nodes.length; i++) {
                Node node = nodes[i];
                Objects.requireNonNull(node, String.format("Node[%d] must not be null", i));
                if (StringUtils.isBlank(node.getUid())) {
                    throw new InvalidParameterException(String.format("Found empty uuid in node[%d]", i));
                }

                if (inputs.containsKey(node.getUid())) {
                    throw new InvalidParameterException(String.format("Duplicate nodes: %s", node.getUid()));
                }

                Content content = contents[i];
                Objects.requireNonNull(content, String.format("Content[%d] node must not be null", i));

                String cname = content.getContentPropertyPrefixedName();
                Objects.requireNonNull(cname, String.format("Content[%d] property name must not be null", i));

                PrefixedQName qname = PrefixedQName.valueOf(cname);
                if (StringUtils.isBlank(qname.getNamespaceURI()) || StringUtils.isBlank(qname.getLocalPart())) {
                    throw new InvalidParameterException(String.format("Invalid content[%d] property name: %s", i, cname));
                }

                inputs.put(node.getUid(), new ContentRequest(node.getUid(), cname));
            }
        });

        return call(context, () -> {
            Map<String,NodeAttachment> attachmentMap = nodeContentService.getNodeContents(inputs.values(), MASSIVE_MAX_RETRIEVE_SIZE);
            List<File> files = new LinkedList<>();
            for (Map.Entry<String,ContentRequest> entry : inputs.entrySet()) {
                final String uuid = entry.getKey();
                NodeAttachment a = attachmentMap.get(uuid);
                if (a == null) {
                    throw new NoSuchNodeException(uuid);
                }

                var cname = Optional.ofNullable(inputs.get(uuid)).map(ContentRequest::getContentPropertyName).orElse(null);
                if (entry.getValue() == null || !StringUtils.equals(a.getContentProperty().getName(), cname)) {
                    throw new InvalidParameterException(
                        String.format("Provided content property name '%s' doesn't match with '%s' for uuid %s", cname, a.getContentProperty().getName(), uuid));
                }

                files.add(a.getFile());
            }

            return files.toArray(new File[0]);
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public ContentData[] massiveRetrieveContentData(Node[] nodes, Content[] contents, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, ReadException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        var files = massiveRetrieveFiles(nodes, contents, context);

        try {
            var results = new ContentData[files.length];
            for (int i = 0; i < files.length; i++) {
                try (FileInputStream is = new FileInputStream(files[i])) {
                    ContentData data = new ContentData();
                    data.setContent(is.readAllBytes());
                    results[i] = data;
                }
            }

            return results;
        } catch (IOException e) {
            throw new ReadException(e.getMessage());
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public String directUploadMethod(@TraceParam(ignore = true) Attachment myFile, String usr, String pwd, String repo, String uuid, String prefixedName) throws SystemException {
        try {
            MtomOperationContext context = new MtomOperationContext();
            context.setUsername(usr);
            context.setPassword(pwd);
            context.setRepository(repo);
            context.setFruitore(usr);
            context.setNomeFisico(usr);

            validate(() -> {
                Objects.requireNonNull(myFile, "Attachment must not be null");
                Objects.requireNonNull(myFile.fileName, "Node name must not be null");
                Objects.requireNonNull(myFile.fileType, "Mimetype must not be null");
            });

            return call(context, () -> {
                var cs = new ContentStream();
                cs.setName(prefixedName);
                cs.setMimetype(myFile.fileType);
                cs.setFileName(myFile.fileName);
                cs.setInputStream(myFile.attachmentDataHandler.getInputStream());
                nodeContentService.setNodeContent(uuid, cs, null);
                return uuid;
            });
        } catch (RuntimeException e) {
            try (var is = myFile.attachmentDataHandler.getInputStream()) {
                log.debug("Consuming ignored attachment");
            } catch (IOException ioe) {
                log.error("Could not read attachment data {}", ioe.getMessage());
            }

            throw new SystemException(e.getMessage());
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public String uploadMethod(Attachment myFile, String usr, String pwd, String repo, String parent) throws SystemException {
        try {
            MtomOperationContext context = new MtomOperationContext();
            context.setUsername(usr);
            context.setPassword(pwd);
            context.setRepository(repo);
            context.setFruitore(usr);
            context.setNomeFisico(usr);

            validate(() -> {
                Objects.requireNonNull(myFile, "Attachment must not be null");
                Objects.requireNonNull(myFile.fileName, "Node name must not be null");
                Objects.requireNonNull(myFile.fileType, "Mimetype must not be null");
            });
            
            return call(context, () -> {
                var cs = new ContentStream();
                cs.setEncoding(StandardCharsets.UTF_8.name());
                cs.setMimetype(myFile.fileType);
                cs.setName(CM_CONTENT);
                cs.setInputStream(myFile.attachmentDataHandler.getInputStream());

                var link = new LinkItemRequest();
                link.setTypeName(CM_CONTAINS);
                link.setRelationship(RelationshipKind.PARENT);
                link.setHard(true);
                link.setName("cm:" + myFile.fileName);
                link.setVertexUUID(parent);

                var input = new LinkedInputNodeRequest();
                input.getProperties().put(cs.getName(), cs);
                input.getAssociations().add(link);
                input.setTypeName(CM_CONTENT);
                input.getAspects().addAll(List.of(ASPECT_ECMSYS_STREAMEDCONTENT, CM_TITLED));
                input.getProperties().put(CM_NAME, myFile.fileName);
                input.getProperties().put(CM_TITLE, myFile.fileName);
                input.getProperties().put(CM_DESCRIPTION, "Content loaded by browser");

                return nodeService.createNode(input);
            });
        } catch (RuntimeException e) {
            try (var is = myFile.attachmentDataHandler.getInputStream()) {
                log.debug("Consuming ignored attachment");
            } catch (IOException ioe) {
                log.error("Could not read attachment data {}", ioe.getMessage());
            }

            throw new SystemException(e.getMessage());
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    public void updateContentData(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, UpdateException, NoSuchNodeException,
        EcmEngineTransactionException, InvalidCredentialsException, PermissionDeniedException {
        validate(node);
        validate(() -> {
            Objects.requireNonNull(content, "Content must not be null");
            Objects.requireNonNull(content.getContent(), "Content buffer must not be null");
            Objects.requireNonNull(content.getMimeType(), "Content mimetype must not be null");
        });

        call(context, () -> {
            var cs = new ContentStream();
            cs.setName(content.getContentPropertyPrefixedName());
            cs.setMimetype(content.getMimeType());
            cs.setEncoding(content.getEncoding());
            cs.setInputStream(new ByteArrayInputStream(content.getContent()));
            nodeContentService.setNodeContent(node.getUid(), cs, null);
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public FileInfo getFileInfo(Node node, NodeInfo nodeInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoSuchNodeException,
        PermissionDeniedException, SearchException {
        validate(node);

        validate(() -> {
            requireNonNull(nodeInfo, "Node info");
            requireNonNull(nodeInfo.getPrefixedName(), "Node info prefixed name");

            PrefixedQName qname = PrefixedQName.valueOf(nodeInfo.getPrefixedName());
            if (StringUtils.isBlank(qname.getNamespaceURI()) || StringUtils.isBlank(qname.getLocalPart())) {
                throw new InvalidParameterException("Invalid content property name: " + nodeInfo.getPrefixedName());
            }
        });

        return call(context, () -> {
            var a  = nodeContentService.getNodeContent(node.getUid(), nodeInfo.getPrefixedName());
            var result = new FileInfo();
            result.setPath(a.getFile().getAbsolutePath());
            result.setSize(a.getFile().length());
            result.setModified(new Date(a.getFile().lastModified()));

            return result;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public FileFormatInfo[] getFileFormatInfo(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException {

        validate(node);

        String cname = content.getContentPropertyPrefixedName();
        validate(() -> {
            requireNonNull(content, "Content node");
            requireNonNull(cname, "Content property name");

            PrefixedQName qname = PrefixedQName.valueOf(cname);
            if (StringUtils.isBlank(qname.getNamespaceURI()) || StringUtils.isBlank(qname.getLocalPart())) {
                throw new InvalidParameterException("Invalid content property name: " + cname);
            }
        });

        return call(context, () -> {
            try {
                var contentRef = new ContentRef();
                contentRef.setUuid(node.getUid());
                contentRef.setContentPropertyName(cname);
                log.debug("Analyzing {}", contentRef);
                var result = incorrect(contentMapper.asFileFormatInfoArray(documentAnalyzer.getFileFormat(contentRef)));
                for (var fi : result) {
                    fi.setUid(node.getUid());
                }
                return result;
            } catch (AnalysisException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public FileFormatInfo[] getFileFormatInfoFileInfo(FileInfo fileInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException {
        validate(() -> {
            Objects.requireNonNull(fileInfo, "FileInfo must not be null");
            Objects.requireNonNull(fileInfo.getContents(), "FileInfo buffer must not be null");
        });

        return call(context, () -> TransactionService.current().doOnTemp(() -> TransactionService.current().requireNew(() -> {
            // create an ephemeral content
            var descriptor = new ContentDescriptor();
            descriptor.setFileName(fileInfo.getName());
            descriptor.setName(CM_CONTENT);
            var ephemeralContent = temporaryService.createEphemeralNode(descriptor, new ByteArrayInputStream(fileInfo.getContents()), null);

            // perform analyze of the just created content
            try {
                var contentRef = new ContentRef();
                contentRef.setUuid(ephemeralContent.getUuid());
                contentRef.setContentPropertyName(descriptor.getName());
                var result = incorrect(contentMapper.asFileFormatInfoArray(documentAnalyzer.getFileFormat(contentRef)));
                if (fileInfo.isStore()) {
                    for (var fi : result) {
                        fi.setUid(ephemeralContent.getUuid());
                        temporaryService.unephemeralize(ephemeralContent.getUuid());
                    }
                }

                return result;
            } catch (AnalysisException e) {
                throw new RuntimeException(e);
            }
        })));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public FileFormatInfo[] identifyDocument(Document document, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException {
        validate(() -> Objects.requireNonNull(document, "Document must not be null"));

        try {
            if (document.getUid() == null) {
                Objects.requireNonNull(document.getBuffer(), "Document's buffer must not be null");
                var fileInfo = new FileInfo();
                fileInfo.setContents(document.getBuffer());
                fileInfo.setStore(Optional.ofNullable(document.getOperation()).map(DocumentOperation::isTempStore).orElse(false));
                return getFileFormatInfoFileInfo(fileInfo, context);
            } else {
                Objects.requireNonNull(document.getContentPropertyPrefixedName(), "Document's CPPN must not be null");
                var content = new Content();
                content.setContentPropertyPrefixedName(document.getContentPropertyPrefixedName());
                return getFileFormatInfo(new Node(document.getUid()), content, context);
            }
        } catch (NullPointerException e) {
            throw new InvalidParameterException(e.getMessage());
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public FileReport getFileReport(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException {

        validate(node);

        String cname = content.getContentPropertyPrefixedName();
        validate(() -> {
            requireNonNull(content, "Content node");
            requireNonNull(cname, "Content property name");

            PrefixedQName qname = PrefixedQName.valueOf(cname);
            if (StringUtils.isBlank(qname.getNamespaceURI()) || StringUtils.isBlank(qname.getLocalPart())) {
                throw new InvalidParameterException("Invalid content property name: " + cname);
            }
        });

        return call(context, () -> {
            try {
                var contentRef = new ContentRef();
                contentRef.setUuid(node.getUid());
                contentRef.setContentPropertyName(cname);
                var result = incorrect(contentMapper.asFileReport(documentAnalyzer.getFileFormat(contentRef)));
                for (var fi : result.getFormats()) {
                    fi.setUid(node.getUid());
                }
                return result;
            } catch (AnalysisException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public FileReport getFileReportStream(FileInfo fileInfo, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, EcmEngineException, RemoteException {

        validate(() -> {
            Objects.requireNonNull(fileInfo, "FileInfo must not be null");
            Objects.requireNonNull(fileInfo.getContents(), "FileInfo buffer must not be null");
        });

        return call(context, () -> TransactionService.current().doOnTemp(() -> TransactionService.current().requireNew(() -> {
            // create an ephemeral content
            var descriptor = new ContentDescriptor();
            descriptor.setFileName(fileInfo.getName());
            descriptor.setName(CM_CONTENT);
            var ephemeralContent = temporaryService.createEphemeralNode(descriptor, new ByteArrayInputStream(fileInfo.getContents()), null);

            // perform analyze of the just created content
            try {
                var contentRef = new ContentRef();
                contentRef.setUuid(ephemeralContent.getUuid());
                contentRef.setContentPropertyName(descriptor.getName());
                var result = incorrect(contentMapper.asFileReport(documentAnalyzer.getFileFormat(contentRef)));
                if (fileInfo.isStore()) {
                    for (var fi : result.getFormats()) {
                        fi.setUid(ephemeralContent.getUuid());
                        temporaryService.unephemeralize(ephemeralContent.getUuid());
                    }
                }

                return result;
            } catch (AnalysisException e) {
                throw new RuntimeException(e);
            }
        })));
    }

}

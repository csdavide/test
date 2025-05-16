package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.cxf.mappers.ContentMapper;
import it.doqui.libra.librabl.business.service.document.DocumentStream;
import it.doqui.libra.librabl.business.service.interfaces.*;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.node.*;
import it.doqui.libra.librabl.views.renditions.RenditionNode;
import it.doqui.libra.librabl.views.renditions.RenditionSettings;
import it.doqui.libra.librabl.views.renditions.TransformerIdentifiedInputRequest;
import it.doqui.libra.librabl.views.renditions.TransformerNode;
import jakarta.activation.DataHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.rmi.RemoteException;
import java.util.Objects;
import java.util.Optional;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;

@ApplicationScoped
@Slf4j
public class XSLTServiceBridge extends AbstractServiceBridge {

    @Inject
    RenditionService renditionService;

    @Inject
    ContentMapper contentMapper;

    @Inject
    TemporaryService temporaryService;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public RenditionTransformer[] getRenditionTransformers(Node xml, String propertyContent, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(xml);
        return call(context, () -> renditionService
            .findRenditionTransformers(new ContentRequest(xml.getUid(), propertyContent))
            .stream()
            .map(this::asRenditionTransformer)
            .toList()
            .toArray(new RenditionTransformer[0]));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public RenditionTransformer getRenditionTransformer(Node nodoTransformer, String propertyContent, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException,
        RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(nodoTransformer);
        return call(context, () -> renditionService
            .getRenditionTransformer(new ContentRequest(nodoTransformer.getUid(), propertyContent))
            .map(this::asRenditionTransformer)
            .orElseThrow(() -> new NoSuchNodeException(nodoTransformer.getUid())));
    }

    @Deprecated
    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public RenditionDocument getRendition(Node nodoTransformer, String propertyContent, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        throw new EcmEngineTransactionException("Deprecated method");
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public RenditionDocument[] getNodeRenditions(Node nodoxml, Node nodoTransformer, MtomOperationContext context) {
        validate(nodoxml);
        validate(nodoTransformer);

        return call(context, () -> renditionService
            .findRenditionNodes(
                new ContentRequest(nodoxml.getUid(), null),
                new ContentRequest(nodoTransformer.getUid(), null),
                null,
                true)
            .stream()
            .map(this::asRenditionDocument)
            .toList()
            .toArray(new RenditionDocument[0])
        );
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public RenditionDocument[] getRenditions(Node xml, String propertyContent, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, RemoteException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        validate(xml);
        return call(context, () -> renditionService
            .findRenditionNodes(new ContentRequest(xml.getUid(), propertyContent), null, null, true))
            .stream()
            .map(this::asRenditionDocument)
            .toList()
            .toArray(new RenditionDocument[0]);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node addRenditionTransformer(Node nodoXml, RenditionTransformer renditionTransformer, String propertyContent,
                                        MtomOperationContext context) throws InvalidParameterException, InsertException, NoSuchNodeException,
        RemoteException, InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(nodoXml);
        validate(() -> {
            Objects.requireNonNull(renditionTransformer, "Rendition transformer must not be null");
            if (renditionTransformer.getNodeId() == null) {
                Objects.requireNonNull(renditionTransformer.getContent(), "Content must not be null");
                Objects.requireNonNull(renditionTransformer.getPrefixedName(), "PrefixedName must not be null");
                Objects.requireNonNull(renditionTransformer.getTypePrefixedName(), "TypePrefixedName must not be null");
            } else {
                Objects.requireNonNull(renditionTransformer.getContentPropertyPrefixedName(), "Content property prefixed name must not be null");
            }
        });

        return call(context, () -> {
            var input = contentMapper.asInputNodeRequest(renditionTransformer, TransformerIdentifiedInputRequest.class);
            var settings = new RenditionSettings();
            var contentRef = mapToContentRef(renditionTransformer, settings);

            var ecd = setExternalSource(contentRef, renditionTransformer);
            input.getProperties().put(Optional.ofNullable(ecd.getName()).orElse(CM_CONTENT), ecd);

            if (renditionTransformer.getDescription() != null) {
                input.getProperties().put(PROP_ECMSYS_TRANSFORMER_DESCRIPTION, renditionTransformer.getDescription());
            }
            if (renditionTransformer.getGenMymeType() != null) {
                input.getProperties().put(Constants.PROP_ECMSYS_GENMIMETYPE, renditionTransformer.getGenMymeType());
            }

            input.setTypeName(renditionTransformer.getTypePrefixedName());
            input.getAssociations().stream().findFirst().ifPresent(a -> a.setName(renditionTransformer.getPrefixedName()));
            var rtNode = renditionService.createAndAssignTransformer(new ContentRequest(nodoXml.getUid(), propertyContent), input);
            return new Node(rtNode.getUuid());
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void deleteRenditionTransformer(Node xml, Node renditionTransformer, MtomOperationContext context)
        throws InvalidParameterException, DeleteException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(xml);
        validate(renditionTransformer);

        call(context, () -> {
            renditionService.deleteTransformer(
                new ContentRequest(xml.getUid(), null),
                new ContentRequest(renditionTransformer.getUid(), null)
            );
            return null;
        });
    }

    @Deprecated
    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node setRendition(Node nodoTransformer, RenditionDocument renditionDocument, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        throw new EcmEngineTransactionException("Deprecated method");
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Node setNodeRendition(Node nodoxml, Node nodoTransformer, RenditionDocument renditionDocument, MtomOperationContext context)
        throws InvalidParameterException, InsertException, NoSuchNodeException, RemoteException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        validate(nodoxml);
        validate(nodoTransformer);
        validate(() -> {
            if (renditionDocument != null) {
                if (renditionDocument.getNodeId() == null) {
                    Objects.requireNonNull(renditionDocument.getContent(), "Content must not be null");
                    Objects.requireNonNull(renditionDocument.getPrefixedName(), "PrefixedName must not be null");
                    Objects.requireNonNull(renditionDocument.getTypePrefixedName(), "TypePrefixedName must not be null");
                } else {
                    Objects.requireNonNull(renditionDocument.getContentPropertyPrefixedName(), "Content property prefixed name must not be null");
                }
            }
        });

        return call(context, () -> {
            if (renditionDocument == null) {
                renditionService.deleteRenditions(
                    new ContentRequest(nodoxml.getUid(), null),
                    new ContentRequest(nodoTransformer.getUid(), null),
                    false
                );
                return null;
            } else {
                var settings = new RenditionSettings();
                var input = contentMapper.asInputNodeRequest(renditionDocument, LinkedInputNodeRequest.class);

                var ref = mapToContentRef(renditionDocument, settings, false);

                var ecd = setExternalSource(ref, renditionDocument);
                input.getProperties().put(Optional.ofNullable(ecd.getName()).orElse(CM_CONTENT), ecd);

                if (renditionDocument.getDescription() != null) {
                    input.getProperties().put(PROP_ECMSYS_RENDITION_DESCRIPTION, renditionDocument.getDescription());
                }

                input.setTypeName(renditionDocument.getTypePrefixedName());
                input.getAssociations().stream().findFirst().ifPresent(a -> a.setName(renditionDocument.getPrefixedName()));

                var rdNode = renditionService.setNodeRendition(
                    new ContentRequest(nodoxml.getUid(), null),
                    new ContentRequest(nodoTransformer.getUid(), null),
                    input
                );
                return new Node(rdNode.getUuid());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public byte[] generateRenditionRenditionDocument(RenditionDocument renditionDocument, RenditionTransformer renditionTransformer, MtomOperationContext context)
        throws NoSuchNodeException, InvalidParameterException, PermissionDeniedException, TransformException {
        validate(() -> {
            Objects.requireNonNull(renditionDocument, "RenditionDocument must not be null");
            Objects.requireNonNull(renditionTransformer, "RenditionTransformer must not be null");

            if (renditionDocument.getNodeId() != null) {
                Objects.requireNonNull(renditionDocument.getContentPropertyPrefixedName(), "RenditionDocument's CPPN must not be null");
            } else {
                Objects.requireNonNull(renditionDocument.getContent(), "RenditionDocument's content must not be null");
                Objects.requireNonNull(renditionDocument.getMimeType(), "RenditionDocument's mimetype must not be null");
            }

            if (renditionTransformer.getNodeId() != null) {
                Objects.requireNonNull(renditionTransformer.getContentPropertyPrefixedName(), "RenditionTransformer's CPPN must not be null");
            } else {
                Objects.requireNonNull(renditionTransformer.getContent(), "RenditionTransformer's content must not be null");
                Objects.requireNonNull(renditionTransformer.getMimeType(), "RenditionTransformer's mimetype must not be null");
            }
        });

        return call(context, () -> {
            var settings = new RenditionSettings();
            settings.setUnwrap(false);
            settings.setForceGeneration(true);
            settings.setResultMimetype(renditionTransformer.getGenMymeType());

            var transformerRef = mapToContentRef(renditionTransformer, settings);
            var xmlRef = mapToContentRef(renditionDocument, settings, true);

            return renditionService.generateRendition(transformerRef, xmlRef, settings).getBinaryData();
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public RenditionDocument generateRenditionContent(Content xml, RenditionTransformer renditionTransformer, MtomOperationContext context)
        throws InvalidParameterException {
        validate(() -> {
            Objects.requireNonNull(renditionTransformer, "TransformerNode must not be null");
            Objects.requireNonNull(xml, "XML content must not be null");

            if (renditionTransformer.getNodeId() != null) {
                Objects.requireNonNull(renditionTransformer.getContentPropertyPrefixedName(), "TransformerNode's CPPN must not be null");
            } else {
                Objects.requireNonNull(renditionTransformer.getContent(), "TransformerNode's content must not be null");
                Objects.requireNonNull(renditionTransformer.getMimeType(), "TransformerNode's mimetype must not be null");
            }

            Objects.requireNonNull(xml.getContent(), "Physical XML content must not be null");
            Objects.requireNonNull(xml.getMimeType(), "Mimetype of the XML content must not be null");
        });

        return call(context, () -> {
            var settings = new RenditionSettings();
            settings.setResultMimetype(renditionTransformer.getGenMymeType());
            settings.setForceGeneration(true);
            settings.setUnwrap(false);
            settings.setRenditionableTempNode(true);

            var transformerRef = mapToContentRef(renditionTransformer, settings);

            var documentStream = new DocumentStream();
            documentStream.setInputStream(new ByteArrayInputStream(xml.getContent()));
            documentStream.setMimeType(xml.getMimeType());

            var xmlRef = temporaryService.createEphemeralNode(documentStream);

            var rendition = renditionService.generateRendition(transformerRef, xmlRef, settings);
            return asRenditionDocument(rendition);
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public DataHandler generateRendition(RenditionData inRenditionData, boolean toBeExtracted, String usr, String pwd, String repo) {
        validate(() -> {
            Objects.requireNonNull(inRenditionData, "Input data must not be null");
            Objects.requireNonNull(inRenditionData.getRtNodeId(), "Transformer node must not be null");
            Objects.requireNonNull(inRenditionData.getRtContentPropertyPrefixedName(), "Transformer's CPPN must not be null");
//            Objects.requireNonNull(inRenditionData.getGenMymeType(), "Transformer's genMimetype must not be null");
            if (inRenditionData.getNodeId() != null) {
                Objects.requireNonNull(inRenditionData.getContentPropertyPrefixedName(), "XML's CPPN must not be null");
            } else {
                Objects.requireNonNull(inRenditionData.getContent(), "XML content must not be null");
                Objects.requireNonNull(inRenditionData.getMimeType(), "XML mimetype must not be null");
            }
        });

        var context = new MtomOperationContext(usr, pwd, usr, usr, repo);
        return call(context, () -> {
            var settings = new RenditionSettings();
            settings.setForceGeneration(true);
            settings.setResultMimetype(inRenditionData.getGenMymeType());
            settings.setUnwrap(toBeExtracted);

            var transformerRef = new ContentRef()
                .setUuid(inRenditionData.getRtNodeId())
                .setContentPropertyName(inRenditionData.getRtContentPropertyPrefixedName());

            var xmlRef = new ContentRef();
            if (inRenditionData.getNodeId() != null) {
                xmlRef.setUuid(inRenditionData.getNodeId());
                xmlRef.setContentPropertyName(inRenditionData.getContentPropertyPrefixedName());
            } else {
                var documentStream = new DocumentStream();
                documentStream.setInputStream(new ByteArrayInputStream(inRenditionData.getContent()));
                documentStream.setMimeType(inRenditionData.getMimeType());

                xmlRef = temporaryService.createEphemeralNode(documentStream);
                settings.setRenditionableTempNode(true);
            }

            var rendition = renditionService.generateRendition(xmlRef, transformerRef, settings);
            return new DataHandler(new ByteArrayDataSource(rendition.getBinaryData(), inRenditionData.getGenMymeType()));
        });
    }

    private RenditionTransformer asRenditionTransformer(TransformerNode n) {
        var rt = new RenditionTransformer();
        rt.setNodeId(n.getUuid());
        rt.setDescription(n.getDescription());
        rt.setGenMymeType(n.getGenMimeType());
        rt.setMimeType(n.getMimeType());
        rt.setContent(n.getBinaryData());
        return rt;
    }

    private RenditionDocument asRenditionDocument(RenditionNode r) {
        var t = new RenditionDocument();
        t.setNodeId(r.getUuid());
        t.setDescription(r.getDescription());
        t.setMimeType(r.getMimeType());
        t.setContent(r.getBinaryData());

        var properties = new Property[1];
        var property = new Property();
        property.setPrefixedName(PROP_ECMSYS_GENERATED);
        property.setValues(new String[]{String.valueOf(r.getGenerated() != null && r.getGenerated())});
        properties[0] = property;
        t.setProperties(properties);
        return t;
    }

    private ContentRef mapToContentRef(RenditionTransformer renditionTransformer, RenditionSettings settings) {
        if (renditionTransformer.getNodeId() != null) {
            settings.setTransformerTempNode(renditionTransformer.isTempNode());
            return new ContentRef()
                .setUuid(renditionTransformer.getNodeId())
                .setTenant(renditionTransformer.isTempNode() ? temporaryService.getTemporaryTenant() : null)
                .setContentPropertyName(renditionTransformer.getContentPropertyPrefixedName());
        } else {
            settings.setTransformerTempNode(true);
            var documentStream = new DocumentStream();
            documentStream.setInputStream(new ByteArrayInputStream(renditionTransformer.getContent()));
            documentStream.setMimeType(renditionTransformer.getMimeType());
            documentStream.setDescription(renditionTransformer.getDescription());

            return temporaryService.createEphemeralNode(documentStream);
        }
    }

    private ContentRef mapToContentRef(RenditionDocument renditionDocument, RenditionSettings settings, boolean renditionable) {
        if (renditionDocument.getNodeId() != null) {
            if (renditionable) {
                settings.setRenditionableTempNode(renditionDocument.isTempNode());
            } else {
                settings.setRenditionTempNode(renditionDocument.isTempNode());
            }

            return new ContentRef()
                .setUuid(renditionDocument.getNodeId())
                .setTenant(renditionDocument.isTempNode() ? temporaryService.getTemporaryTenant() : null)
                .setContentPropertyName(renditionDocument.getContentPropertyPrefixedName());
        } else {
            if (renditionable) {
                settings.setRenditionableTempNode(true);
            } else {
                settings.setRenditionTempNode(true);
            }

            var documentStream = new DocumentStream();
            documentStream.setInputStream(new ByteArrayInputStream(renditionDocument.getContent()));
            documentStream.setMimeType(renditionDocument.getMimeType());
            documentStream.setDescription(renditionDocument.getDescription());

            return temporaryService.createEphemeralNode(documentStream);
        }
    }

    private ExternalContentDescriptor setExternalSource(ContentRef ref, Content content) {
        var ecd = new ExternalContentDescriptor();
        var source = new ExternalContentDescriptor.ExternalSource();
        source.setRef(ref);
        ecd.setName(Optional.ofNullable(content.getContentPropertyPrefixedName()).orElse(CM_CONTENT));
        ecd.setMimetype(content.getMimeType());
        ecd.setEncoding(content.getEncoding());
        ecd.setSource(source);
        return ecd;
    }
}

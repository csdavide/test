package it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components;

import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.domain.model.document.DocumentStream;
import it.doqui.libra.librabl.domain.model.document.FileType;
import it.doqui.libra.librabl.domain.model.document.SignatureType;
import it.doqui.libra.librabl.domain.model.exceptions.DocumentOperationException;
import it.doqui.libra.librabl.application.ports.in.DocumentAnalyzeUseCase;
import it.doqui.libra.librabl.application.ports.in.DocumentUseCase;
import it.doqui.libra.librabl.application.ports.in.ContentUseCase;
import it.doqui.libra.librabl.application.ports.in.TemporaryUseCase;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.dto.FileTypeResponse;
import it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.dto.SignatureTypeResponse;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.application.model.document.*;
import it.doqui.libra.librabl.domain.model.document.DocumentOperation;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

@IfBuildProperty(name = "libra.module.cxf.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
public class DocumentServiceBridge extends AbstractServiceBridge {

    @Inject
    DocumentUseCase documentService;

    @Inject
    DocumentAnalyzeUseCase documentAnalyzer;

    @Inject
    TemporaryUseCase temporaryUseCase;

    @Inject
    ContentUseCase contentUseCase;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public DigestInfo generateDigestFromContent(Content content, DigestInfo digestInfo, MtomOperationContext context)
        throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException {
        validate(() -> {
            requireNonNull(content, "Content");
            Objects.requireNonNull(content.getContent(), "Content buffer must exist");
            requireNonNull(digestInfo, "DigestInfo");
            Objects.requireNonNull(digestInfo.getAlgorithm(), "DigestInfo's algorithm must be specified");
        });

        return call(context, () -> {
            var digestResponse = documentService.digest(new ByteArrayInputStream(content.getContent()), digestInfo.getAlgorithm());
            var result = new DigestInfo();
            result.setDigest(digestResponse.getDigest());
            result.setAlgorithm(digestResponse.getAlg());
            return result;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public DigestInfo generateDigestFromUID(
        Node node,
        NodeInfo nodeInfo,
        DigestInfo digestInfo,
        MtomOperationContext context)
        throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException,
        NoSuchNodeException, SearchException {
        validate(node);
        validate(() -> {
            requireNonNull(nodeInfo, "NodeInfo");
            Objects.requireNonNull(nodeInfo.getPrefixedName(), "NodeInfo's prefixed name must be specified");
            requireNonNull(digestInfo, "DigestInfo");
            Objects.requireNonNull(digestInfo.getAlgorithm(), "Algorithm must be specified");
        });

        return call(context, () -> {
            var digestResponse = documentService.digest(new ContentRef().setUuid(node.getUid()), nodeInfo.isEnveloped(), digestInfo.getAlgorithm());
            var result = new DigestInfo();
            result.setDigest(digestResponse.getDigest());
            result.setAlgorithm(digestResponse.getAlg());
            return result;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public boolean compareDigest(Node node, Node tempNode, NodeInfo nodeInfo, NodeInfo tempNodeInfo, DigestInfo digestInfo, MtomOperationContext context)
        throws InvalidCredentialsException, InvalidParameterException, PermissionDeniedException, ReadException,
        NoSuchNodeException, SearchException, EcmEngineTransactionException {

        validate(node);
        validate(tempNode);
        validate(() -> {
            requireNonNull(nodeInfo, "NodeInfo");
            requireNonNull(tempNodeInfo, "TempNodeInfo");
            requireNonNull(digestInfo, "DigestInfo");
            requireNonNull(digestInfo.getAlgorithm(), "Algorithm");
        });

        return call(context, () -> {
            var nodeDigest = documentService.digest(
                new ContentRef().setUuid(node.getUid()),
                nodeInfo.isEnveloped(),
                digestInfo.getAlgorithm());

            var tempDigest = documentService.digest(
                new ContentRef().setTenant(temporaryUseCase.getTemporaryTenant()).setUuid(tempNode.getUid()),
                tempNodeInfo.isEnveloped(),
                digestInfo.getAlgorithm());

            return Strings.CS.equals(nodeDigest.getDigest(), tempDigest.getDigest());
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public VerifyReport verifySignedDocument(
        Document document,
        Document detachedSignature,
        VerifyParameter verifyParameter,
        MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, EcmEngineTransactionException {

        var response = performDocumentVerification(document, detachedSignature, verifyParameter, context, OperationMode.SYNC);
        if (response.getStatus() == DocumentOperationResponse.SignOperationStatus.SUBMITTED) {
            throw new RuntimeException("Unexpected verification status " + response.getStatus());
        }

        try {
            return getVerifyReport(response);
        } catch (IOException e) {
            throw new EcmEngineTransactionException("Unable to deserialize report: " + e.getMessage());
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public String verifyAsyncDocument(
        Document document,
        Document detachedSignature,
        VerifyParameter verifyParameter,
        MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        var response = performDocumentVerification(document, detachedSignature, verifyParameter, context, OperationMode.ASYNC);

        return response.getRequestId();
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public VerifyReportExtDto verifyDocumentExt(Document document, Document detachedSignature,
                                         VerifyParameter verifyParameter, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        var response = performDocumentVerification(document, detachedSignature, verifyParameter, context, OperationMode.AUTO);

        if (isInErrorStatus(response.getStatus())) {
            throw new EcmEngineTransactionException("Dosign exception: invalid document");
        }

        var result = new VerifyReportExtDto();
        result.setTokenUuid(response.getRequestId());
        try {
            result.setReport(getVerifyReport(response));
        } catch (IOException e) {
            throw new EcmEngineTransactionException("Unable to deserialize report: " + e.getMessage());
        }

        return result;
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public VerifyReport verifyDocumentNode(Node[] node, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, EcmEngineTransactionException {
        return verifyDocumentNodeVerifyParameter(node, null, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public VerifyReport verifyDocumentNodeVerifyParameter(Node[] node, VerifyParameter verifyParameter,
                                                          MtomOperationContext context) throws InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, NoSuchNodeException, EcmEngineTransactionException {
        validate(node);
        if (node.length < 1) {
            throw new InvalidParameterException("No node specified");
        }

        var document = new Document();
        document.setUid(node[0].getUid());

        final Document detachedSignature;
        if (node.length > 1) {
            detachedSignature = new Document();
            detachedSignature.setUid(node[1].getUid());
        } else {
            detachedSignature = null;
        }

        return verifySignedDocument(document, detachedSignature, verifyParameter, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public VerifyReport verifyDocument(EnvelopedContent envelopedContent, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        return verifyDocumentVerifyParameter(envelopedContent, null, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public VerifyReport verifyDocumentVerifyParameter(EnvelopedContent envelopedContent,
                                               VerifyParameter verifyParameter, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        validate(() -> {
            requireNonNull(envelopedContent, "Enveloped Content");
            requireNonNull(envelopedContent.getData(), "Enveloped Content Data");
        });

        var document = new Document();
        document.setBuffer(envelopedContent.getData());
        var op = new it.doqui.index.ecmengine.mtom.dto.DocumentOperation();
        op.setTempStore(envelopedContent.isStore());
        op.setReturnData(false);
        document.setOperation(op);
        document.setBuffer(envelopedContent.getData());

        final Document detachedSignature;
        if (envelopedContent.getDetachedData() != null && envelopedContent.getDetachedData().length > 0) {
            detachedSignature = new Document();
            var op2 = new it.doqui.index.ecmengine.mtom.dto.DocumentOperation();
            op2.setTempStore(envelopedContent.isStore());
            op2.setReturnData(false);
            detachedSignature.setOperation(op2);
            detachedSignature.setBuffer(envelopedContent.getDetachedData());
        } else {
            detachedSignature = null;
        }

        return verifySignedDocument(document, detachedSignature, verifyParameter, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public AsyncReportDto getAsyncReport(String tokenUid) throws InvalidParameterException, NoSuchNodeException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {
        try {
            var response = documentService.getVerificationReport(tokenUid);
            if (response.getStatus() == DocumentOperationResponse.SignOperationStatus.SUBMITTED) {
                throw new RuntimeException("Unexpected verification status " + response.getStatus());
            }

            AsyncReportDto result = new AsyncReportDto();
            result.setStatus(Status.valueOf(response.getStatus().toString()));
            result.setReport(getVerifyReport(response));

            return result;
        } catch (DocumentOperationException | IOException e) {
            throw new EcmEngineTransactionException(e.getMessage());
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public AsyncReportDto getAsyncReportExt(String tokenUid, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException, RemoteException {
        return call(context, () -> getAsyncReport(tokenUid));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Document extractDocumentFromEnvelope(EnvelopedContent envelopedContent, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        validate(() -> {
            requireNonNull(envelopedContent, "Enveloped Content");
            requireNonNull(envelopedContent.getData(), "Enveloped Content Data");
        });

        var envelopedDocument = new Document();
        envelopedDocument.setBuffer(envelopedContent.getData());
        envelopedDocument.setOperation(new it.doqui.index.ecmengine.mtom.dto.DocumentOperation(envelopedContent.isStore(), true));
        return extractDocumentFromEnvelopeEnvelopedDocument(envelopedDocument, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Document extractDocumentFromEnvelopeNode(Node node, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        validate(node);

        var envelopedDocument = new Document();
        envelopedDocument.setUid(node.getUid());
        envelopedDocument.setOperation(new it.doqui.index.ecmengine.mtom.dto.DocumentOperation(true, false));
        return extractDocumentFromEnvelopeEnvelopedDocument(envelopedDocument, context);
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public Document extractDocumentFromEnvelopeEnvelopedDocument(Document envelopedDocument, MtomOperationContext context)
        throws InsertException, NoSuchNodeException, InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineTransactionException {

        if (envelopedDocument == null) {
            throw new InvalidParameterException("Document is required");
        } else if (envelopedDocument.getUid() != null) {
            if (envelopedDocument.getBuffer() != null) {
                throw new InvalidParameterException("uuid and buffer cannot be both filled");
            }
        } else if (envelopedDocument.getBuffer() == null) {
            throw new InvalidParameterException("Either document uuid or buffer must not be null");
        }

        return call(context, () -> transactionManagerPort.requireNew(() -> {
            var op = Optional.ofNullable(envelopedDocument.getOperation()).orElse(new it.doqui.index.ecmengine.mtom.dto.DocumentOperation(false, false));
            final String uuid;
            if (envelopedDocument.getBuffer() != null) {
                return extractDocument(envelopedDocument.getBuffer(), op);
            } else {
                uuid = envelopedDocument.getUid();
                return extractDocument(uuid, op);
            }
        }));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public SigilloSignedExt sigilloSignatureExt(Document document, SigilloSigner params, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        validate(() -> {
            requireNonNull(document, "Document");
            if (document.getUid() != null) {
                requireNonNull(document.getContentPropertyPrefixedName(), "ContentProperty PrefixedName");
            } else {
                Objects.requireNonNull(document.getBuffer(), "Either UID and ContentProperty PrefixedName or buffer must be set");
            }
            Objects.requireNonNull(params, "SealSigner is mandatory");
        });

        return call(context, () -> {
            var sealParams = new SealParams(
                params.getType(),
                params.getDelegatedDomain(),
                params.getDelegatedPassword(),
                params.getDelegatedUser(),
                params.getIdenv(),
                params.getUser(),
                params.getOtpPwd(),
                params.getTypeOtpAuth(),
                params.getTypeHSM()
            );

            final ContentRef documentRef;
            if (document.getBuffer() != null) {
                documentRef = temporaryUseCase.createEphemeralNode(new DocumentStream().setInputStream(new ByteArrayInputStream(document.getBuffer())));
            } else {
                documentRef = new ContentRef().setUuid(document.getUid()).setContentPropertyName(document.getContentPropertyPrefixedName());
            }

            var mode = context.getNomeFisico() != null && context.getNomeFisico().equals("0") ? OperationMode.ASYNC : OperationMode.AUTO;

            var storeParams = new StoreParams();
            storeParams.setMode(document.getOperation() != null && document.getOperation().isTempStore() ? StoreResultMode.EPHEMERAL : StoreResultMode.NONE);
            storeParams.setReturnData(document.getOperation() != null && document.getOperation().isReturnData());
            try {
                var response = documentService.sealDocument(documentRef, sealParams, storeParams, null, mode);

                var result = new SigilloSignedExt();
                result.setTokenUid(response.getRequestId());
                result.setSigned(getSignedSeal(response));
                return result;
            } catch (IOException e) {
                throw new EcmEngineTransactionException("Unable to deserialize report: " + e.getMessage());
            } catch (DocumentOperationException e) {
                throw new EcmEngineTransactionException(e.getMessage());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public AsyncSigillo getAsyncSigillo(String tokenUid, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {
        return call(context, () -> {
            try {
                var response = documentService.getSealingReport(tokenUid);

                if (response.getStatus() == DocumentOperationResponse.SignOperationStatus.SUBMITTED) {
                    throw new RuntimeException("Unexpected sealing status " + response.getStatus());
                }

                var result = new AsyncSigillo();
                var signedSeal = getSignedSeal(response);
                result.setStatus(Status.valueOf(response.getStatus().toString()));
                if (signedSeal != null) {
                    result.setSigned(signedSeal.getData());
                }

                return result;
            } catch (DocumentOperationException | IOException e) {
                throw new EcmEngineTransactionException(e.getMessage());
            }
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public String getSignatureType(Node node, Content content, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        NoSuchNodeException, ReadException {
        validate(() -> {
            Objects.requireNonNull(content, "Content must not be null");
            if (node == null || node.getUid() == null || Strings.CS.equals(node.getUid(), "FT24")) {
                Objects.requireNonNull(content.getContent(), "Content bytes must not be null");
                if (content.getContent().length == 0) {
                    throw new InvalidParameterException("Content bytes must not be empty");
                }
            }
            if (content.getContent() == null) {
                Objects.requireNonNull(content.getContentPropertyPrefixedName(), "ContentProperty PrefixedName must not be null");
            }
        });

        return call(context, () -> {
            var userIdentity = sessionContext.getUserIdentity();
            if (Strings.CI.equals("FT24", userIdentity) || (node != null && Strings.CI.equals(node.getUid(), "FT24"))) {
                return getFileType(node, content, context);
            }

            InputStream toAnalyze;
            if (content.getContent() == null && node != null) {
                var contentRef = new ContentRef().setUuid(node.getUid()).setContentPropertyName(content.getContentPropertyPrefixedName());
                var attachment = contentUseCase.getNodeContent(contentRef);
                toAnalyze = Files.newInputStream(attachment.getFile().toPath());
            } else {
                toAnalyze = new ByteArrayInputStream(content.getContent());
            }
            return incorrect(mapToSignatureType(documentAnalyzer.getSignatureType(toAnalyze)));
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public String getSignatureTypeData(byte[] data, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException {
        validate(() -> Objects.requireNonNull(data, "Document buffer must not be null"));

        return call(context, () -> incorrect(mapToSignatureType(documentAnalyzer.getSignatureType(new ByteArrayInputStream(data)))));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public String getFileType(Node node, Content content, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, NoSuchNodeException, ReadException {
        validate(() -> {
            Objects.requireNonNull(content, "Content must not be null");
            if (node == null || node.getUid() == null) {
                Objects.requireNonNull(content.getContent(), "Content bytes must not be null");
                if (content.getContent().length == 0) {
                    throw new InvalidParameterException("Content bytes must not be empty");
                }
            }
            if (content.getContent() == null) {
                Objects.requireNonNull(content.getContentPropertyPrefixedName(), "ContentProperty PrefixedName must not be null");
            }
        });

        return call(context, () -> {
            InputStream toAnalyze;
            if (content.getContent() == null) {
                var contentRef = new ContentRef().setUuid(node.getUid()).setContentPropertyName(content.getContentPropertyPrefixedName());
                var attachment = contentUseCase.getNodeContent(contentRef);
                toAnalyze = Files.newInputStream(attachment.getFile().toPath());
            } else {
                toAnalyze = new ByteArrayInputStream(content.getContent());
            }
            return mapToFileType(documentAnalyzer.getSignatureType(toAnalyze));
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public VerifyCertificateReport verifyCertificate(CertBuffer certBuffer, VerifyParameter verifyParameter, MtomOperationContext context)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, InsertException, EcmEngineTransactionException {
        validate(() -> {
            Objects.requireNonNull(certBuffer, "certBuffer must not be null");
            Objects.requireNonNull(certBuffer.getData(), "Buffer data must not be null");
        });

        return call(context, () -> {
            final ContentRef createdRef;
            if (certBuffer.isStore()) {
                createdRef = temporaryUseCase.createEphemeralNode(new DocumentStream().setInputStream(new ByteArrayInputStream(certBuffer.getData())));
            } else {
                createdRef = null;
            }

            final CertificateParams params;
            if (verifyParameter != null) {
                params = new CertificateParams(verifyParameter.getVerificationScope(), verifyParameter.getVerificationType(), verifyParameter.getProfileType());
            } else {
                params = null;
            }

            var certStream = new DocumentStream();
            certStream.setInputStream(new ByteArrayInputStream(certBuffer.getData()));
            var response = documentService.verifyCertificate(certStream, params);
            return Optional.ofNullable(response.getOpaque())
                .map(opaque -> {
                    if (opaque instanceof VerifyCertificateReport report) {
                        if (createdRef != null) {
                            report.setUid(createdRef.getUuid());
                            temporaryUseCase.unephemeralize(createdRef.getUuid());
                        }
                        return report;
                    }

                    return null;
                })
                .orElse(null);
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public String getJobError(String tokenUid, String type, MtomOperationContext context)
        throws InvalidParameterException, EcmEngineException, RemoteException {
        validate(() -> {
            Objects.requireNonNull(tokenUid, "TokenUid must not be null");
            Objects.requireNonNull(type, "Token type must not be null");
        });

        DocumentOperation documentOperation;
        if (type.equalsIgnoreCase("vr")) {
            documentOperation = DocumentOperation.VERIFY;
        } else if (type.equalsIgnoreCase("sd")) {
            documentOperation = DocumentOperation.SEAL;
        } else {
            throw new InvalidParameterException("Type " + type + " not recognized.");
        }
        return call(context, () -> documentService.getErrorReport(tokenUid, documentOperation).getError());
    }

    private DocumentOperationResponse performDocumentVerification(
        Document document,
        Document detachedSignature,
        VerifyParameter verifyParameter,
        MtomOperationContext context,
        OperationMode mode)
        throws InvalidParameterException, NoSuchNodeException, InvalidCredentialsException,
        PermissionDeniedException, EcmEngineTransactionException {

        validate(() -> {
            requireNonNull(document, "Document");
            if (document.getUid() == null && document.getBuffer() == null) {
                throw new NullPointerException("Either document uuid or buffer must not be null");
            } else if (document.getUid() != null && document.getBuffer() != null) {
                throw new NullPointerException("Document uuid and buffer cannot both be filled");
            }

            if (detachedSignature != null) {
                if (detachedSignature.getUid() == null && detachedSignature.getBuffer() == null) {
                    throw new NullPointerException("Either detached document uuid or buffer must not be null");
                } else if (detachedSignature.getUid() != null && detachedSignature.getBuffer() != null) {
                    throw new NullPointerException("Document detached uuid and buffer cannot both be filled");
                }
            }
        });

        return call(context, () -> {
            try {
                final ZonedDateTime verificationDate;
                if (verifyParameter != null && verifyParameter.getVerificationDate() != null) {
                    verificationDate = ZonedDateTime.ofInstant(verifyParameter.getVerificationDate().toInstant(), ZoneId.systemDefault());
                } else {
                    verificationDate = null;
                }

                var createdContents = new HashMap<String, ContentRef>();
                final ContentRef documentRef;
                if (document.getBuffer() != null) {
                    documentRef = temporaryUseCase.createEphemeralNode(new DocumentStream().setInputStream(new ByteArrayInputStream(document.getBuffer())));
                    if (document.getOperation() != null && document.getOperation().isTempStore()) {
                        createdContents.put("doc", documentRef);
                    }
                } else {
                    documentRef = new ContentRef().setUuid(document.getUid()).setContentPropertyName(document.getContentPropertyPrefixedName());
                }

                final ContentRef detachedDocumentRef;
                if (detachedSignature == null) {
                    detachedDocumentRef = null;
                } else if (detachedSignature.getBuffer() != null) {
                    detachedDocumentRef = temporaryUseCase.createEphemeralNode(new DocumentStream().setInputStream(new ByteArrayInputStream(detachedSignature.getBuffer())));
                    if (detachedSignature.getOperation() != null && detachedSignature.getOperation().isTempStore()) {
                        createdContents.put("detached", detachedDocumentRef);
                    }
                } else {
                    detachedDocumentRef = new ContentRef().setUuid(detachedSignature.getUid()).setContentPropertyName(detachedSignature.getContentPropertyPrefixedName());
                }

                var response = documentService.verifyDocument(documentRef, detachedDocumentRef, verificationDate, null, mode);
                response.getCreatedContents().addAll(createdContents.values());
                if (document.getOperation().isTempStore()) {
                    temporaryUseCase.unephemeralize(Optional.ofNullable(createdContents.get("doc")).map(ContentRef::getUuid).orElse(null));
                }
                if (detachedSignature != null && detachedSignature.getOperation() != null && detachedSignature.getOperation().isTempStore()) {
                    temporaryUseCase.unephemeralize(Optional.ofNullable(createdContents.get("detached")).map(ContentRef::getUuid).orElse(null));
                }

                return response;
            } catch (IOException | DocumentOperationException e) {
                throw new EcmEngineTransactionException(e.getMessage());
            }
        });
    }

    private boolean isInErrorStatus(DocumentOperationResponse.SignOperationStatus status) {
        return Objects.equals(status, DocumentOperationResponse.SignOperationStatus.ERROR)
            || Objects.equals(status, DocumentOperationResponse.SignOperationStatus.EXPIRED);
    }

    private VerifyReport getVerifyReport(DocumentOperationResponse response) throws IOException {
        if (response.getOpaque() instanceof VerifyReport vr) {
            if (!response.getCreatedContents().isEmpty()) {
                vr.setUid(response.getCreatedContents().stream().map(ContentRef::getUuid).toList().toArray(new String[0]));
            }

            return vr;
        } else if (response.getStatus().equals(DocumentOperationResponse.SignOperationStatus.NULL)) {
            return new VerifyReport();
        }

        return null;
    }

    private SigilloSigned getSignedSeal(DocumentOperationResponse response) throws IOException {
        if (response.getOpaque() instanceof byte[] data) {
            var ss = new SigilloSigned();
            ss.setData(data);
            if (!response.getCreatedContents().isEmpty()) {
                ss.setUid(response.getCreatedContents().stream().map(ContentRef::getUuid).findFirst().orElse(null));
                temporaryUseCase.unephemeralize(ss.getUid());
            }
            return ss;
        }

        return null;
    }

    private Document extractDocument(String uuid, it.doqui.index.ecmengine.mtom.dto.DocumentOperation op) {
        try {
            var inContentRef = new ContentRef().setUuid(uuid);
            var result = new Document();
            if (op.isTempStore()) {
                var outContentRef = documentService.unwrapIntoNode(new ContentRef().setUuid(uuid));
                result.setUid(outContentRef.getUuid());
                temporaryUseCase.unephemeralize(outContentRef.getUuid());

                if (op.isReturnData()) {
                    var attachment = contentUseCase.getNodeContent(outContentRef);
                    result.setBuffer(IOUtils.readFully(Files.newInputStream(attachment.getFile().toPath())));
                }
            } else {
                var extractedStream = documentService.unwrap(inContentRef);
                if (op.isReturnData()) {
                    result.setBuffer(IOUtils.readFully(extractedStream.getInputStream()));
                }
            }

            return result;
        } catch (IOException e) {
            throw new SystemException(e);
        } catch (DocumentOperationException e) {
            throw new PermissionDeniedException(e.getMessage());
        }
    }

    private Document extractDocument(byte[] buffer, it.doqui.index.ecmengine.mtom.dto.DocumentOperation op) {
        try {
            var extractedStream = documentService.unwrap(new ByteArrayInputStream(buffer));
            var result = new Document();
            if (op.isTempStore()) {
                var outContentRef = temporaryUseCase.createEphemeralNode(extractedStream);
                result.setUid(outContentRef.getUuid());
                temporaryUseCase.unephemeralize(outContentRef.getUuid());

                if (op.isReturnData()) {
                    var attachment = contentUseCase.getNodeContent(outContentRef);
                    result.setBuffer(IOUtils.readFully(Files.newInputStream(attachment.getFile().toPath())));
                }
            } else if (op.isReturnData()) {
                result.setBuffer(IOUtils.readFully(extractedStream.getInputStream()));
            }

            return result;
        } catch (IOException e) {
            throw new SystemException(e);
        } catch (DocumentOperationException e) {
            throw new PermissionDeniedException(e.getMessage());
        }
    }

    private String mapToFileType(FileCharacteristics fileCharacteristics) {
        if (fileCharacteristics != null) {
            if (fileCharacteristics.getSignatureType() == SignatureType.XADES) {
                return FileTypeResponse.XADES.name();
            }
            if (fileCharacteristics.getSignatureType() == SignatureType.CADES) {
                return FileTypeResponse.CADES.name();
            }
            if (fileCharacteristics.getSignatureType() == SignatureType.TIMESTAMP) {
                return FileTypeResponse.TIMESTAMP.name();
            }
            if (fileCharacteristics.getFileType() == FileType.PDF_PROTECTED) {
                return FileTypeResponse.PDF_PROTECTED.name();
            }
            if (fileCharacteristics.getSignatureType() == SignatureType.PADES) {
                return FileTypeResponse.PADES.name();
            }
            if (fileCharacteristics.getFileType() == FileType.PDF && fileCharacteristics.getSignatureType() == SignatureType.UNSIGNED) {
                return FileTypeResponse.PDF.name();
            }
        }
        return FileTypeResponse.UNKNOWN.name();
    }

    private String mapToSignatureType(FileCharacteristics fileCharacteristics) {
        if (fileCharacteristics != null) {
            if (fileCharacteristics.getFileType() == FileType.XML && fileCharacteristics.getSignatureType() == SignatureType.XADES) {
                return SignatureTypeResponse.XML.name();
            }
            if (fileCharacteristics.getFileType() == FileType.P7M && fileCharacteristics.getSignatureType() == SignatureType.CADES) {
                return SignatureTypeResponse.PKCS.name();
            }
            if (fileCharacteristics.getFileType() == FileType.TIMESTAMPED && fileCharacteristics.getSignatureType() == SignatureType.TIMESTAMP) {
                return SignatureTypeResponse.TIMESTAMPED.name();
            }
            if (
                (fileCharacteristics.getFileType() == FileType.PDF
                || fileCharacteristics.getFileType() == FileType.PDF_PROTECTED
                )
                    && fileCharacteristics.getSignatureType() == SignatureType.PADES) {
                return SignatureTypeResponse.PDF.name();
            }
        }
        return SignatureTypeResponse.UNSIGNED.name();
    }

    //if (DIANA && PKCS) return PCKS
    private String incorrect(String signatureData) {
        if (Strings.CI.equals(signatureData, "PKCS")) {
            return "PCKS";
        }
        return signatureData;
    }
}

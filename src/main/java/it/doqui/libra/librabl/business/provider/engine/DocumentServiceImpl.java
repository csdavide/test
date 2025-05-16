package it.doqui.libra.librabl.business.provider.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.dosign.dosign.business.session.dosign.*;
import it.doqui.dosign.dosign.business.session.dosign.asyncservice.DosignAsyncSignatureValidation;
import it.doqui.dosign.dosign.business.session.dosign.defered.DosignDefered;
import it.doqui.dosign.dosign.business.session.dosign.defered.SealDocumentInDto;
import it.doqui.dosign.dosign.business.session.dosign.defered.VerifyDocumentInDto;
import it.doqui.index.ecmengine.mtom.dto.VerifyReport;
import it.doqui.libra.librabl.business.provider.integration.dosign.SoapClientProducer;
import it.doqui.libra.librabl.business.service.document.DocumentStream;
import it.doqui.libra.librabl.business.service.exceptions.SignOperationException;
import it.doqui.libra.librabl.business.service.interfaces.DocumentAnalyzer;
import it.doqui.libra.librabl.business.service.interfaces.DocumentService;
import it.doqui.libra.librabl.business.service.interfaces.NodeContentService;
import it.doqui.libra.librabl.business.service.interfaces.TemporaryService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.document.*;
import it.doqui.libra.librabl.views.node.ContentRef;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static it.doqui.dosign.dosign.business.session.dosign.defered.DeferedStatus.ERROR;

@ApplicationScoped
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private Dosign dosign;
    private DosignDefered dosignD;
    private DosignAsyncSignatureValidation dosignAsync;
    private final String regex = "^(.*)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$";

    @Inject
    NodeContentService nodeContentService;

    @ConfigProperty(name = "libra.dosign.endpoints.dosign")
    String dosignWsdl;

    @ConfigProperty(name = "libra.dosign.endpoints.dosignDefered")
    String dosignDWsdl;

    @ConfigProperty(name = "libra.dosign.endpoints.dosignAsync")
    String dosignAsyncWsdl;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TemporaryService temporaryService;

    @Inject
    SoapClientProducer clientProducer;

    @Inject
    DocumentAnalyzer documentAnalyzer;

    @ConfigProperty(name = "libra.dosign.operation.timeout", defaultValue = "15s")
    Duration operationTimeout;

    @ConfigProperty(name = "libra.dosign.seal.default-id-env")
    String placement;

    @ConfigProperty(name = "libra.dosign.token.verify", defaultValue = "vr-LIBRA-")
    String verifyToken;

    @ConfigProperty(name = "libra.dosign.token.seal", defaultValue = "sd-LIBRA-")
    String sealToken;

    @PostConstruct
    public void init() {
        dosign = clientProducer.createClient(Dosign.class, dosignWsdl);
        dosignD = clientProducer.createClient(DosignDefered.class, dosignDWsdl);
        dosignAsync = clientProducer.createClient(DosignAsyncSignatureValidation.class, dosignAsyncWsdl);
    }

    @Override
    public ContentRef unwrapIntoNode(ContentRef contentRef) throws IOException, SignOperationException {
        return temporaryService.createEphemeralNode(unwrap(contentRef));
    }

    @Override
    public DocumentStream unwrap(ContentRef contentRef) throws IOException, SignOperationException {
        var a = nodeContentService.getNodeContent(contentRef);
        if (a.isOpaque()) {
            throw new PreconditionFailedException("The specified content is opaque");
        }

        try (var inNodeStream = Files.newInputStream(a.getFile().toPath())) {
            var outDocumentStream = unwrap(inNodeStream);
            outDocumentStream.setFileName(a.getName());
            return outDocumentStream;
        }
    }

    @Override
    public DocumentStream unwrap(InputStream stream) throws IOException, SignOperationException {
        var eb = new EnvelopedBuffer();
        eb.setBuffer(IOUtils.readFully(stream));
        try {
            log.debug("Sending buffer to dosign having MD5 {} and size {} bytes", ObjectUtils.hash(eb.getBuffer(), "MD5"), eb.getBuffer().length);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        try {
            final Document unwrappedDocument;
            if (isSignedXML(eb.getBuffer())) {
                log.debug("xml identified. Calling dosign...");
                unwrappedDocument = dosign.extractDocumentFromXmlEnvelope(eb);
            } else {
                log.debug("Calling dosign...");
                unwrappedDocument = dosign.extractDocumentFromEnvelope(eb);
            }

            var payload = new DocumentStream();
            var tika = new Tika();
            payload.setMimeType(tika.detect(unwrappedDocument.getBuffer()));
            payload.setInputStream(new ByteArrayInputStream(unwrappedDocument.getBuffer()));

            return payload;
        } catch (DosignException_Exception e) {
            log.error("Dosign has thrown exception: {}: {}", e.getMessage(), Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(null));
            throw new SystemException(e);
        }
    }

    @Override
    public DigestResponse digest(ContentRef contentRef, boolean enveloped, String algorithm) throws NoSuchAlgorithmException, IOException {
        var a = nodeContentService.getNodeContent(contentRef);
        try (var stream = Files.newInputStream(a.getFile().toPath())) {
            final InputStream inStream;
            if (enveloped) {
                if (a.isOpaque()) {
                    throw new PreconditionFailedException("The specified content is encrypted");
                }

                var unwrappedStream = unwrap(stream);
                inStream = unwrappedStream.getInputStream();
            } else {
                inStream = stream;
            }

            return digest(inStream, algorithm);
        } catch (SignOperationException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public DigestResponse digest(InputStream stream, String algorithm) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        var data = new byte[1024];
        int byteRead;
        while ((byteRead = stream.read(data)) != -1) {
            md.update(data, 0, byteRead);
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }

        return new DigestResponse(algorithm, sb.toString());
    }

    @Override
    public DocumentSignOperationResponse verifyDocument(ContentRef documentRef, ContentRef detachedDocumentRef, ZonedDateTime verifyAt, Duration timeout, OperationMode mode) throws IOException, SignOperationException {
        var documentAttachment = nodeContentService.getNodeContent(documentRef);
        if (documentAttachment.isOpaque()) {
            throw new PreconditionFailedException("The specified content is encrypted");
        }

        NodeAttachment detachedDocumentAttachment = null;
        if (detachedDocumentRef != null) {
            detachedDocumentAttachment = nodeContentService.getNodeContent(detachedDocumentRef);
            if (detachedDocumentAttachment.isOpaque()) {
                throw new PreconditionFailedException("The detached document cannot be encrypted");
            }
        }

        if (Objects.equals(mode, OperationMode.SYNC)) {
            var signedBuffer = new SignedBuffer();
            try (var fis = new FileInputStream(documentAttachment.getFile())) {
                signedBuffer.setBuffer(fis.readAllBytes());
            }

            if (detachedDocumentAttachment != null) {
                try (var fis = new FileInputStream(detachedDocumentAttachment.getFile())) {
                    signedBuffer.setDetachedBuffer(fis.readAllBytes());
                }
            }

            try {
                it.doqui.dosign.dosign.business.session.dosign.VerifyReport vdOut;
                if (isSignedXML(signedBuffer.getBuffer())) {
                    vdOut = dosign.verifyDocumentXml(signedBuffer);
                } else {
                    vdOut = dosign.verifyDocument(signedBuffer);
                }

                var response = new DocumentSignOperationResponse();
                if (vdOut != null) {
                    response.setOpaque(objectMapper.readValue(objectMapper.writeValueAsBytes(vdOut), VerifyReport.class));
                } else {
                    response.setStatus(DocumentSignOperationResponse.SignOperationStatus.NULL);
                }
                return response;
            } catch (DosignException_Exception e) {
                log.error("Dosign has thrown exception: {}: {}", e.getMessage(), Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(null));
                throw new SystemException(e);
            }

        } else {
            var vdIn = new VerifyDocumentInDto();
            vdIn.setSigned(insertDocument(documentAttachment));

            if (detachedDocumentAttachment != null) {
                vdIn.setDetached(insertDocument(detachedDocumentAttachment));
            }

            vdIn.setTimeWait(setTimeout(mode, timeout));
            vdIn.setTokenUid(verifyToken + UUID.randomUUID());
            vdIn.setNotifyUrl("#");

            if (verifyAt == null) {
                verifyAt = ZonedDateTime.now();
            }
            try {
                var gc = new GregorianCalendar();
                gc.setTimeInMillis(verifyAt.toInstant().toEpochMilli());
                var d = DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
                vdIn.setDataVerifica(d);
            } catch (DatatypeConfigurationException e) {
                throw new SystemException(e);
            }

            try {
                var vdOut = dosignD.verifyDocument(vdIn);
                var response = new DocumentSignOperationResponse();
                if (!Objects.equals(mode, OperationMode.ASYNC) && vdOut.getReport() != null) {
                    response.setOpaque(objectMapper.readValue(objectMapper.writeValueAsBytes(vdOut.getReport()), VerifyReport.class));
                } else {
                    if (Objects.equals(mode, OperationMode.ASYNC)) {
                        response.setStatus(DocumentSignOperationResponse.SignOperationStatus.SUBMITTED);
                    } else {
                        response.setStatus(DocumentSignOperationResponse.mapStatus(vdOut.getStatus()));
                    }
                    response.setRequestId(ObjectUtils.takeRegexPart(vdOut.getTokenUid(), regex, 2));
                    if (response.getRequestId() == null) {
                        throw new SystemException("Invalid token: " + vdOut.getTokenUid());
                    }
                }
                return response;

            } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception e) {
                if (Objects.equals(mode, OperationMode.ASYNC)) {
                    var response = new DocumentSignOperationResponse();
                    response.setStatus(DocumentSignOperationResponse.SignOperationStatus.ERROR);
                    response.setRequestId(ObjectUtils.takeRegexPart(vdIn.getTokenUid(), regex, 2));
                    if (response.getRequestId() == null) {
                        throw new SystemException("Invalid token: " + vdIn.getTokenUid());
                    }
                    return response;
                } else {
                    throw new SignOperationException(e);
                }
            }
        }
    }

    @Override
    public DocumentSignOperationResponse getVerificationReport(String requestId) throws SignOperationException {
        try {
            var result = dosignD.getVerifyReport(verifyToken + requestId);
            DocumentSignOperationResponse response = new DocumentSignOperationResponse();
            response.setStatus(DocumentSignOperationResponse.SignOperationStatus.valueOf(result.getStatus().value()));
            if (response.getStatus() == DocumentSignOperationResponse.SignOperationStatus.READY) {
                response.setOpaque(objectMapper.readValue(objectMapper.writeValueAsBytes(result.getReport()), VerifyReport.class));
            }

            return response;
        } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception e) {
            throw new SignOperationException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DocumentSignOperationResponse verifyCertificate(DocumentStream certStream, CertificateParams params) throws SignOperationException, IOException {
        try {

            var certBuffer = new CertBuffer();
            certBuffer.setBuffer(IOUtils.readFully(certStream.getInputStream()));

            VerifyParameter verifyParameter = null;
            if (params != null) {
                verifyParameter = new VerifyParameter();
                verifyParameter.setProfileType(params.getProfileType());
                verifyParameter.setVerificationScope(params.getVerificationScope());
                verifyParameter.setProfileType(params.getProfileType());

                var gc = new GregorianCalendar();
                if (params.getVerificationDate() != null) {
                    gc.setTimeInMillis(params.getVerificationDate().toInstant().toEpochMilli());
                } else {
                    gc.setTimeInMillis(ZonedDateTime.now().toInstant().toEpochMilli());
                }
                var d = DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
                verifyParameter.setVerificationDate(d);
            }

            var response = new DocumentSignOperationResponse();
            response.setOpaque(dosign.verifyCertificate(certBuffer, verifyParameter));
            return response;
        } catch (RuntimeException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new SignOperationException(e);
        }
    }

    @Override
    public DocumentSignOperationResponse sealDocument(ContentRef documentRef, SealParams params, Duration timeout, OperationMode mode) throws SignOperationException, IOException {
        var sealSignature = convertToDosignParams(params);

        var documentAttachment = nodeContentService.getNodeContent(documentRef);
        if (documentAttachment.isOpaque()) {
            throw new PreconditionFailedException("The specified content is encrypted");
        }

        var sealIn = new SealDocumentInDto();
        sealIn.setSealData(sealSignature);
        sealIn.setDocument(insertDocument(documentAttachment));
        sealIn.setTimeWait(setTimeout(mode, timeout));
        sealIn.setTokenUid(sealToken + UUID.randomUUID());

        try {
            var sealOut = dosignD.sealDocument(sealIn);
            var response = new DocumentSignOperationResponse();
            var token = ObjectUtils.takeRegexPart(sealOut.getTokenUid(), regex, 2);

            if (Objects.equals(mode, OperationMode.ASYNC)) {
                response.setRequestId(token);
                if (response.getRequestId() == null) {
                    throw new SystemException("Invalid token: " + sealOut.getTokenUid());
                }
                response.setStatus(DocumentSignOperationResponse.SignOperationStatus.SUBMITTED);
            } else if (Objects.equals(sealOut.getStatus(), ERROR)) {
                try {
                    var errorMsg = dosignAsync.getJobError(token);
                    throw new it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception(errorMsg);
                } catch (it.doqui.dosign.dosign.business.session.dosign.asyncservice.DosignException_Exception e) {
                    throw new it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception(e.getMessage());
                }
            } else if (sealOut.getDocument() != null) {
                byte[] contentResult = IOUtils.readFully(sealOut.getDocument().getInputStream());
                response.setOpaque(contentResult);
                if (params.isStoreResult()) {
                    var nodeResult = temporaryService.createEphemeralNode(new DocumentStream().setInputStream(new ByteArrayInputStream(contentResult)));
                    response.getCreatedContents().add(nodeResult);
                }
            } else {
                throw new SystemException("Buffer document is null");
            }
            return response;

        } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception e) {
            throw new SignOperationException(e);
        }
    }

    @Override
    public DocumentSignOperationResponse getSealingReport(String requestId) throws SignOperationException {
        try {
            var result = dosignD.getSealedDocument(sealToken + requestId);

            DocumentSignOperationResponse response = new DocumentSignOperationResponse();
            response.setStatus(DocumentSignOperationResponse.SignOperationStatus.valueOf(result.getStatus().value()));
            if (response.getStatus() == DocumentSignOperationResponse.SignOperationStatus.READY) {
                response.setOpaque(IOUtils.readFully(result.getDocument().getInputStream()));
            }

            return response;
        } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception | IOException e) {
            throw new SignOperationException(e);
        }
    }

    @Override
    public ErrorReport getErrorReport(String tokenUid, SignOperation type) throws SignOperationException {
        try {
            ErrorReport result = new ErrorReport();
            String token;
            if (Objects.equals(type, SignOperation.VERIFY)) {
                result.setOperation(type);
                token = verifyToken + tokenUid;
            } else if (Objects.equals(type, SignOperation.SEAL)) {
                result.setOperation(type);
                token = sealToken + tokenUid;
            } else {
                throw new BadRequestException("Invalid type");
            }

            result.setError(dosignAsync.getJobError(token));
            return result;
        } catch (it.doqui.dosign.dosign.business.session.dosign.asyncservice.DosignException_Exception e) {
            throw new SignOperationException(e);
        }
    }

    private DataHandler insertDocument(NodeAttachment document) {
        return new DataHandler(new DataSource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return Files.newInputStream(document.getFile().toPath());
            }

            @Override
            public OutputStream getOutputStream() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getContentType() {
                return document.getContentProperty().getName();
            }

            @Override
            public String getName() {
                return document.getName();
            }
        });
    }

    private long setTimeout(OperationMode mode, Duration timeout) {
        return switch (mode) {
            case SYNC -> Long.MAX_VALUE;
            case ASYNC -> 0L;
            default -> timeout != null ? timeout.toMillis() : operationTimeout.toMillis();
        };
    }

    private boolean isSignedXML(byte[] data) {
        try {
            try (var bais = new ByteArrayInputStream(data)) {
                var signature = documentAnalyzer.getSignatureType(bais);
                if (signature.getSignatureType() == SignatureType.XADES) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private it.doqui.dosign.dosign.business.session.dosign.defered.SigilloSignatureDto convertToDosignParams(SealParams params) {
        var sealSignature = new it.doqui.dosign.dosign.business.session.dosign.defered.SigilloSignatureDto();
        sealSignature.setType(params.getType());
        sealSignature.setDelegatedDomain(params.getDelegatedDomain());
        sealSignature.setDelegatedUser(params.getDelegatedUser());
        sealSignature.setDelegatedPassword(params.getDelegatedPassword());
        sealSignature.setUser(params.getUser());
        sealSignature.setOtpPwd(params.getOtpPassword());
        sealSignature.setTypeOtpAuth(params.getTypeOtpAuth());
        sealSignature.setTypeHSM(params.getTypeHSM());

        sealSignature.setIdenv(params.getEnvironmentId() == null ? placement : params.getEnvironmentId());
        log.debug("Placement: {}", params.getEnvironmentId() == null ? placement : sealSignature.getIdenv());
        return sealSignature;
    }
}

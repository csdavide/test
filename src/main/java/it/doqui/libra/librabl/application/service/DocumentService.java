package it.doqui.libra.librabl.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.dosign.dosign.business.session.dosign.*;
import it.doqui.dosign.dosign.business.session.dosign.DosignException_Exception;
import it.doqui.dosign.dosign.business.session.dosign.asyncservice.DosignAsyncSignatureValidation;
import it.doqui.dosign.dosign.business.session.dosign.defered.DosignDefered;
import it.doqui.dosign.dosign.business.session.dosign.defered.SealDocumentInDto;
import it.doqui.dosign.dosign.business.session.dosign.defered.VerifyDocumentInDto;
import it.doqui.dosign.dosign.business.session.dosign.remotev2.*;
import it.doqui.index.ecmengine.mtom.dto.VerifyReport;
import it.doqui.libra.librabl.application.mappers.AsyncOperationConverter;
import it.doqui.libra.librabl.application.model.document.*;
import it.doqui.libra.librabl.application.model.document.DigestResponse;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.application.model.graph.NodeItem;
import it.doqui.libra.librabl.application.model.graph.NodePathItem;
import it.doqui.libra.librabl.application.ports.in.DocumentAnalyzeUseCase;
import it.doqui.libra.librabl.application.ports.in.DocumentUseCase;
import it.doqui.libra.librabl.application.ports.in.TemporaryUseCase;
import it.doqui.libra.librabl.application.ports.in.ContentUseCase;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.domain.model.document.*;
import it.doqui.libra.librabl.domain.model.exceptions.DocumentOperationException;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.policy.OperationOption;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.infrastructure.adapters.output.dosign.SoapClientProducer;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.application.model.association.EdgeItem;
import it.doqui.libra.librabl.application.model.association.LinkItemRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.SignJobRequest;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.apache.tika.Tika;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static it.doqui.dosign.dosign.business.session.dosign.defered.DeferedStatus.ERROR;
import static it.doqui.libra.librabl.domain.model.graph.Constants.*;

@ApplicationScoped
@Slf4j
public class DocumentService implements DocumentUseCase {

    private Dosign dosign;
    private DosignDefered dosignD;
    private DosignAsyncSignatureValidation dosignAsync;
    private DosignRemoteService dosignRemoteSign;
    private final String regex = "^(.*)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$";

    @ConfigProperty(name = "libra.dosign.endpoints.dosign")
    String dosignWsdl;

    @ConfigProperty(name = "libra.dosign.endpoints.dosignDefered")
    String dosignDWsdl;

    @ConfigProperty(name = "libra.dosign.endpoints.dosignAsync")
    String dosignAsyncWsdl;

    @ConfigProperty(name = "libra.dosign.endpoints.dosignRemoteSign")
    String dosignRemoteSignWsdl;

    @ConfigProperty(name = "libra.dosign.operation.timeout", defaultValue = "15s")
    Duration operationTimeout;

    @ConfigProperty(name = "libra.dosign.seal.default-id-env")
    String placement;

    @ConfigProperty(name = "libra.dosign.token.verify", defaultValue = "vr-LIBRA-")
    String verifyToken;

    @ConfigProperty(name = "libra.dosign.token.seal", defaultValue = "sd-LIBRA-")
    String sealToken;

    @ConfigProperty(name = "libra.dosign.operation.sign.postVerificationRequired", defaultValue = "false")
    boolean postVerificationRequired;

    @ConfigProperty(name = "libra.dosign.operation.sign.userVerificationRequired", defaultValue = "false")
    boolean userVerificationRequired;

    @ConfigProperty(name = "libra.dosign.operation.sign.userCertificateExpiring", defaultValue = "15s")
    Duration userCertificateExpiring;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TemporaryUseCase temporaryUseCase;

    @Inject
    SoapClientProducer clientProducer;

    @Inject
    DocumentAnalyzeUseCase documentAnalyzer;

    @Inject
    ContentUseCase contentUseCase;

    @Inject
    AttachmentHelper contentRetriever;

    @Inject
    NodeManager nodeManager;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    JobUseCase jobService;

    @Inject
    SessionContext sessionContext;

    @PostConstruct
    public void init() {
        dosign = clientProducer.createClient(Dosign.class, dosignWsdl);
        dosignD = clientProducer.createClient(DosignDefered.class, dosignDWsdl);
        dosignAsync = clientProducer.createClient(DosignAsyncSignatureValidation.class, dosignAsyncWsdl);
        dosignRemoteSign = clientProducer.createClient(DosignRemoteService.class, dosignRemoteSignWsdl);
    }

    @Override
    public ContentRef unwrapIntoNode(ContentRef contentRef) throws IOException, DocumentOperationException {
        return temporaryUseCase.createEphemeralNode(unwrap(contentRef));
    }

    @Override
    public DocumentStream unwrap(ContentRef contentRef) throws IOException, DocumentOperationException {
        var a = contentUseCase.getNodeContent(contentRef);
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
    public DocumentStream unwrap(InputStream stream) throws IOException, DocumentOperationException {
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
            logDosignException(e);
            throw new SystemException(e);
        }
    }

    @Override
    public DigestResponse digest(ContentRef contentRef, boolean enveloped, String algorithm) throws NoSuchAlgorithmException, IOException {
        var a = contentUseCase.getNodeContent(contentRef);
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
        } catch (DocumentOperationException e) {
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
    public DocumentOperationResponse verifyDocument(ContentRef documentRef, ContentRef detachedDocumentRef, ZonedDateTime verifyAt, Duration timeout, OperationMode mode) throws IOException, DocumentOperationException {
        var documentAttachment = contentUseCase.getNodeContent(documentRef);
        if (documentAttachment.isOpaque()) {
            throw new PreconditionFailedException("The specified content is encrypted");
        }

        NodeAttachment detachedDocumentAttachment = null;
        if (detachedDocumentRef != null) {
            detachedDocumentAttachment = contentUseCase.getNodeContent(detachedDocumentRef);
            if (detachedDocumentAttachment.isOpaque()) {
                throw new PreconditionFailedException("The detached document cannot be encrypted");
            }
        }

        if (Objects.equals(mode, OperationMode.SYNC)) {
            return verifyDocumentSync(documentAttachment, detachedDocumentAttachment, null);
        } else {
            return verifyDocumentNotSync(documentAttachment, detachedDocumentAttachment, verifyAt, timeout, mode);
        }
    }

    @Override
    public DocumentOperationResponse getVerificationReport(String requestId) throws DocumentOperationException {
        try {
            var result = dosignD.getVerifyReport(verifyToken + requestId);
            DocumentOperationResponse response = new DocumentOperationResponse();
            response.setStatus(DocumentOperationResponse.SignOperationStatus.valueOf(result.getStatus().value()));
            if (response.getStatus() == DocumentOperationResponse.SignOperationStatus.READY) {
                response.setOpaque(objectMapper.readValue(objectMapper.writeValueAsBytes(result.getReport()), VerifyReport.class));
            }

            return response;
        } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception e) {
            throw new DocumentOperationException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DocumentOperationResponse verifyCertificate(DocumentStream certStream, CertificateParams params) throws DocumentOperationException, IOException {
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

            var response = new DocumentOperationResponse();
            response.setOpaque(dosign.verifyCertificate(certBuffer, verifyParameter));
            return response;
        } catch (RuntimeException | IOException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentOperationException(e);
        }
    }

    @Override
    public DocumentOperationResponse sealDocument(ContentRef documentRef, SealParams sealParams, StoreParams storeParams, Duration timeout, OperationMode mode) throws DocumentOperationException, IOException {
        var sealSignature = convertParams(sealParams);

        var documentAttachment = contentUseCase.getNodeContent(documentRef);
        if (documentAttachment.isOpaque()) {
            throw new PreconditionFailedException("The specified content is encrypted");
        }

        try {
            X509Certificate userCert = null;
            var t0 = System.currentTimeMillis();
            if (userVerificationRequired) {
                userCert = getUserCert(
                    Provider.correctedValueOf(sealSignature.getDelegatedDomain()),
                    sealSignature.getDelegatedUser(),
                    sealSignature.getDelegatedPassword()
                );
                doUserCertificateExpiringVerification(t0, userCert);
            }

            var sealIn = new SealDocumentInDto();
            sealIn.setSealData(sealSignature);
            sealIn.setDocument(insertDocument(documentAttachment));
            sealIn.setTimeWait(setTimeout(mode, timeout));
            sealIn.setTokenUid(sealToken + UuidCreator.getTimeOrderedEpoch());
            var sealOut = dosignD.sealDocument(sealIn);

            var response = new DocumentOperationResponse();
            var token = ObjectUtils.takeRegexPart(sealOut.getTokenUid(), regex, 2, false);

            if (Objects.equals(mode, OperationMode.ASYNC)) {
                response.setRequestId(token);
                if (response.getRequestId() == null) {
                    throw new SystemException("Invalid token: " + sealOut.getTokenUid());
                }
                response.setStatus(DocumentOperationResponse.SignOperationStatus.SUBMITTED);
            } else if (Objects.equals(sealOut.getStatus(), ERROR)) {
                try {
                    var errorMsg = dosignAsync.getJobError(token);
                    throw new it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception(errorMsg);
                } catch (it.doqui.dosign.dosign.business.session.dosign.asyncservice.DosignException_Exception e) {
                    throw new it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception(e.getMessage());
                }
            } else if (sealOut.getDocument() != null) {

                if (!storeParams.getMode().equals(StoreResultMode.NONE)) {
                    var signData = createSignData(
                        DocumentOperation.SEAL,
                        mapSealType(sealSignature.getType()),
                        Provider.correctedValueOf(sealSignature.getDelegatedDomain()),
                        sealSignature.getDelegatedUser()
                        );
                    var nodeResult = storeResult(documentRef.getUuid(), sealOut.getDocument().getInputStream(), storeParams, signData, t0 / 1000L, userCert);
                    response.getCreatedContents().add(nodeResult);
                    if (storeParams.isReturnData()) {
                        byte[] contentResult = IOUtils.readFully(new FileInputStream(contentUseCase.getNodeContent(nodeResult).getFile()));
                        response.setOpaque(contentResult);
                    }
                } else {
                    byte[] contentResult = IOUtils.readFully(sealOut.getDocument().getInputStream());
                    response.setOpaque(contentResult);
                }

            } else {
                //case AUTO con timeout scaduto/non scaduto?
                throw new SystemException("Buffer document is null");
            }
            return response;

        } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception e) {
            throw new DocumentOperationException(e);
        }
    }

    @Override
    public DocumentOperationResponse getSealingReport(String requestId) throws DocumentOperationException {
        try {
            var result = dosignD.getSealedDocument(sealToken + requestId);

            DocumentOperationResponse response = new DocumentOperationResponse();
            response.setStatus(DocumentOperationResponse.SignOperationStatus.valueOf(result.getStatus().value()));
            if (response.getStatus() == DocumentOperationResponse.SignOperationStatus.READY) {
                response.setOpaque(IOUtils.readFully(result.getDocument().getInputStream()));
            }

            return response;
        } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception | IOException e) {
            throw new DocumentOperationException(e);
        }
    }

    @Override
    public ErrorReport getErrorReport(String tokenUid, DocumentOperation type) throws DocumentOperationException {
        try {
            ErrorReport result = new ErrorReport();
            String token;
            if (Objects.equals(type, DocumentOperation.VERIFY)) {
                result.setOperation(type);
                token = verifyToken + tokenUid;
            } else if (Objects.equals(type, DocumentOperation.SEAL)) {
                result.setOperation(type);
                token = sealToken + tokenUid;
            } else {
                throw new BadRequestException("Invalid type");
            }

            result.setError(dosignAsync.getJobError(token));
            return result;
        } catch (it.doqui.dosign.dosign.business.session.dosign.asyncservice.DosignException_Exception e) {
            throw new DocumentOperationException(e);
        }
    }

    @Override
    public DocumentOperationResponse signDocument(ContentRef documentRef, SignParams params, StoreParams storeParams) throws DocumentOperationException, IOException {
        var documentAttachment = contentUseCase.getNodeContent(documentRef);
        if (documentAttachment == null) {
            throw new PreconditionFailedException("Node has no content");
        }
        if (documentAttachment.isOpaque()) {
            throw new PreconditionFailedException("The specified content is encrypted");
        }

        checkSignTypeWithMimetype(documentAttachment.getDescriptor().getMimetype(), params.getSignType());

        try {
            X509Certificate userCert = null;
            var t0 = System.currentTimeMillis();
            if (userVerificationRequired) {
                userCert = getUserCert(params.getProvider(), params.getUsername(), params.getPassword());
                doUserCertificateExpiringVerification(t0, userCert);
            }

            var signInput = new it.doqui.dosign.dosign.business.session.dosign.remotev2.RemoteSignatureDto();
            signInput.setData(insertDocument(documentAttachment));
            signInput.setFormat(mapSignTypeToFormat(params.getSignType()));
            signInput.setOtp(params.getOtp());
            signInput.setTimestamped(verifyTsCredentials(params));
            if (signInput.isTimestamped()) {
                signInput.setTsaUser(params.getTsUsername());
                signInput.setTsaPwd(params.getTsPassword());
                signInput.setTsaUrl(params.getTsUrl());
            }
            signInput.setUsername(params.getUsername());
            signInput.setPassword(params.getPassword());
            signInput.setPin(params.getPin());
            signInput.setEnv(Strings.CI.endsWith(params.getProvider().name(), "cloud") ? Env.CLOUD : Env.BOX);
            signInput.setProvider(
                params.getProvider().name().contains("_")
                    ? params.getProvider().name().substring(0, params.getProvider().name().indexOf("_"))
                    : params.getProvider().name()
            );
            signInput.setCodiceFiscale(params.getCf());
            signInput.setCollocazione(params.getCollocation());

            var signOutput = dosignRemoteSign.sign(signInput);

            var response = new DocumentOperationResponse();
            if (signOutput.getData().getInputStream() != null) {

                if (!storeParams.getMode().equals(StoreResultMode.NONE)) {
                    var signData = createSignData(DocumentOperation.SIGN, params.getSignType(), params.getProvider(), params.getUsername());
                    var nodeResult = storeResult(documentRef.getUuid(), signOutput.getData().getInputStream(), storeParams, signData, t0 / 1000L, userCert);
                    response.getCreatedContents().add(nodeResult);

                    if (storeParams.isReturnData()) {
                        byte[] contentResult = IOUtils.readFully(new FileInputStream(contentUseCase.getNodeContent(nodeResult).getFile()));
                        response.setOpaque(contentResult);
                    }
                } else {
                    byte[] contentResult = IOUtils.readFully(signOutput.getData().getInputStream());
                    response.setOpaque(contentResult);
                }
            } else {
                throw new SystemException("Result signed document is null");
            }
            return response;

        } catch (
            DosignInvalidOtpException_Exception | DosignInvalidModeException_Exception |
            DosignInvalidDataException_Exception | DosignInvalidPinException_Exception |
            it.doqui.dosign.dosign.business.session.dosign.remotev2.DosignException_Exception e) {

            throw new DocumentOperationException(e);
        }
    }

    @Override
    public AsyncOperation<?> submitSignDocument(ContentRef contentRef, SignParams params, StoreParams storeParams) {
        var signStatement = new SignStatement();
        signStatement.setDocumentRef(contentRef);
        signStatement.setSignParams(params);
        signStatement.setStoreParams(storeParams);
        var signRequest = new SignJobRequest();
        signRequest.setSign(signStatement);
        signRequest.setMode(OperationMode.ASYNC);
        return AsyncOperationConverter.map(jobService.executeJob(signRequest));
    }

    @Override
    public void pushOtp(OTPRequest otpRequest) throws DocumentOperationException {
        try {

            var remoteOtpDto = new RemoteOtpDto();
            remoteOtpDto.setUses(otpRequest.getUses());
            remoteOtpDto.setPassword(otpRequest.getPassword());
            remoteOtpDto.setUsername(otpRequest.getUsername());
            remoteOtpDto.setPin(otpRequest.getPin());
            remoteOtpDto.setProvider(
                otpRequest.getProvider().name().contains("_")
                    ? otpRequest.getProvider().name().substring(0, otpRequest.getProvider().name().indexOf("_"))
                    : otpRequest.getProvider().name());
            remoteOtpDto.setEnv(Strings.CI.endsWith(otpRequest.getProvider().name(), "cloud") ? Env.CLOUD : Env.BOX);
            dosignRemoteSign.pushOtp(remoteOtpDto);

        } catch (DosignInvalidAuthenticationException_Exception |
                 DosignInvalidDataException_Exception |
                 it.doqui.dosign.dosign.business.session.dosign.remotev2.DosignException_Exception e) {
            logDosignException(e);
            throw new DocumentOperationException(e);
        }
    }

    private DocumentOperationResponse verifyDocumentSync(NodeAttachment documentAttachment, NodeAttachment detachedDocumentAttachment, Consumer<VerifyReport> verifySomething) throws IOException {
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

            var response = new DocumentOperationResponse();
            if (vdOut != null) {
                var verifyReport = objectMapper.readValue(objectMapper.writeValueAsBytes(vdOut), VerifyReport.class);
                if (verifySomething != null) {
                    verifySomething.accept(verifyReport);
                }
                response.setOpaque(verifyReport);
                response.setStatus(DocumentOperationResponse.SignOperationStatus.READY);
            } else {
                response.setStatus(DocumentOperationResponse.SignOperationStatus.NULL);
            }
            return response;
        } catch (DosignException_Exception e) {
            logDosignException(e);
            throw new SystemException(e);
        }
    }

    private DocumentOperationResponse verifyDocumentNotSync(NodeAttachment documentAttachment, NodeAttachment detachedDocumentAttachment, ZonedDateTime verifyAt, Duration timeout, OperationMode mode) throws IOException, DocumentOperationException {
        var vdIn = new VerifyDocumentInDto();
        vdIn.setSigned(insertDocument(documentAttachment));

        if (detachedDocumentAttachment != null) {
            vdIn.setDetached(insertDocument(detachedDocumentAttachment));
        }

        vdIn.setTimeWait(setTimeout(mode, timeout));
        vdIn.setTokenUid(verifyToken + UuidCreator.getTimeOrderedEpoch());
        vdIn.setNotifyUrl("#");

        try {
            var gc = new GregorianCalendar();
            gc.setTimeInMillis(verifyAt.toInstant().toEpochMilli());
            var d = DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
            vdIn.setDataVerifica(d);
        } catch (DatatypeConfigurationException e) {
            throw new SystemException(e);
        } catch (NullPointerException e) {
            vdIn.setDataVerifica(null);
        }

        try {
            var vdOut = dosignD.verifyDocument(vdIn);
            var response = new DocumentOperationResponse();
            if (!Objects.equals(mode, OperationMode.ASYNC) && vdOut.getReport() != null) {
                response.setOpaque(objectMapper.readValue(objectMapper.writeValueAsBytes(vdOut.getReport()), VerifyReport.class));
                response.setStatus(DocumentOperationResponse.SignOperationStatus.READY);
            } else {
                if (Objects.equals(mode, OperationMode.ASYNC)) {
                    response.setStatus(DocumentOperationResponse.SignOperationStatus.SUBMITTED);
                } else {
                    response.setStatus(DocumentOperationResponse.mapStatus(vdOut.getStatus()));
                }
                response.setRequestId(ObjectUtils.takeRegexPart(vdOut.getTokenUid(), regex, 2, false));
                if (response.getRequestId() == null) {
                    throw new SystemException("Invalid token: " + vdOut.getTokenUid());
                }
            }
            return response;

        } catch (it.doqui.dosign.dosign.business.session.dosign.defered.DosignException_Exception e) {
            if (Objects.equals(mode, OperationMode.ASYNC)) {
                var response = new DocumentOperationResponse();
                response.setStatus(DocumentOperationResponse.SignOperationStatus.ERROR);
                response.setRequestId(ObjectUtils.takeRegexPart(vdIn.getTokenUid(), regex, 2, false));
                if (response.getRequestId() == null) {
                    throw new SystemException("Invalid token: " + vdIn.getTokenUid());
                }
                return response;
            } else {
                throw new DocumentOperationException(e);
            }
        }
    }

    private ContentRef storeResult(String uuid, InputStream stream, StoreParams storeParams, SignData signData, long operationBegin, X509Certificate userCert) {
        ContentRef nodeResult;
        if (storeParams.getMode().equals(StoreResultMode.PATH) && storeParams.getPath() != null && !storeParams.getPath().isEmpty()) {
            nodeResult = createResponseNode(null,
                stream,
                signData,
                storeParams.getPath(),
                storeParams.getFileName() != null && !storeParams.getFileName().isEmpty() ? storeParams.getFileName() : uuid,
                operationBegin,
                userCert);

        } else if (storeParams.getMode().equals(StoreResultMode.REPLACE)) {
            throw new WebException(501, "Not yet implemented");

        } else if (storeParams.getMode().equals(StoreResultMode.NEIGHBOUR)) {
            var options = new HashSet<MapOption>();
            options.add(MapOption.PATHS);
            options.add(MapOption.PARENT_HARD_ASSOCIATIONS);
            var node = nodeManager.getNodeMetadata(new Vertex(VertexType.UUID, uuid), options, null, null).stream().findFirst().orElseThrow(() -> new NotFoundException("Node " + uuid + " not found!"));

            var nodePath = node.getPaths().stream()
                .filter(NodePathItem::isHard)
                .findFirst()
                .map(NodePathItem::getPath)
                .orElseThrow(() -> new NotFoundException("No hard path found!"));

            nodeResult = createResponseNode(
                node,
                stream,
                signData,
                nodePath.substring(0, nodePath.lastIndexOf("/")),
                nodePath.substring(nodePath.lastIndexOf("/") + 1),
                operationBegin,
                userCert);

        } else {
            nodeResult = temporaryUseCase.createEphemeralNode(
                new DocumentStream()
                    .setInputStream(stream)
                    .setMimeType(mapSignTypeToMimetype(signData.getSignType()))
            );
            if (storeParams.getMode().equals(StoreResultMode.PATH)) {
                temporaryUseCase.unephemeralize(nodeResult.getUuid());
            }
        }
        return nodeResult;
    }

    private ContentRef createResponseNode(NodeItem originalNode, InputStream stream, SignData signData, String destinationPath, String name, long operationBegin, X509Certificate userCert) {
        var a = new LinkItemRequest();
        a.setPath(destinationPath);
        a.setCreateIfNotExists(true);
        if (name != null && !name.isEmpty()) {
            var proposedName = createProposedName(name, signData.getDocumentOperation(), signData.getSignType());

            var originalParentUuid = originalNode != null ? originalNode.getParents().stream().findFirst().map(EdgeItem::getVertexUUID).orElse(null) : null;
            var _name = nodeManager.insertTimestampInNameAssociation(
                new Vertex(VertexType.UUID, originalNode != null && originalParentUuid != null ? originalParentUuid : nodeDAO.retrieveUUIDFromPath(destinationPath)),
                proposedName,
                signData.getDocumentOperation().equals(DocumentOperation.SIGN) ? "_signed" : (signData.getDocumentOperation().equals(DocumentOperation.SEAL) ? "_sealed" : null)
            );
            a.setName(_name);
        }

        var cs = new SignedContentStream();
        cs.setInputStream(stream);
        cs.setMimetype(mapSignTypeToMimetype(signData.getSignType()));
        if (originalNode != null && originalNode.getContents() != null) {
            cs.getSigns().addAll(originalNode.getContents().stream().flatMap(x -> x.getSigns().stream()).toList());
        }
        cs.getSigns().add(signData);

        var input = new LinkedInputNodeRequest();
        input.getAssociations().add(a);
        input.setTypeName(CM_CONTENT);
        input.getProperties().put(CM_DESCRIPTION,
            switch (signData.getDocumentOperation()) {
                case SIGN -> "Signed content";
                case SEAL -> "Sealed content";
                default -> "Content";
            } + " created automatically.");

        if (originalNode != null) {
            input.getProperties().putAll(originalNode.getProperties());
            input.getAspects().addAll(originalNode.getAspects());
        }
        input.getProperties().put(CM_CONTENT, cs);
        input.getProperties().put(CM_NAME, PrefixedQName.valueOf(a.getName()).getLocalPart());

        input.getAspects().remove(ASPECT_COPIED_NODE);
        input.getProperties().remove(CM_SOURCE);

        return new ContentRef()
            .setUuid(
                nodeManager.createNode(
                    input,
                    Set.of(OperationOption.IGNORE_MANAGED_PROPERTIES),
                    (tx, node) -> {
                        if (postVerificationRequired) {
                            doPostVerification(node, operationBegin, userCert);
                        }
                    }
                )
            )
            .setTenant(sessionContext.getTenant())
            .setIdentity(sessionContext.getUserIdentity())
            .setContentPropertyName(cs.getName() != null ? cs.getName() : CM_CONTENT)
            .setFileName(a.getName());
    }

    private X509Certificate getUserCert(Provider provider, String username, String password) throws DocumentOperationException {
        if (Strings.CI.startsWith(provider.name(), "UANATACA")) {
            try {
                var input = new RemoteCertsDto();
                input.setType(Type.CERTIFICATE);
                input.setIdentifier("DS0");
                input.setProvider("UANATACA");
                input.setUsername(username);
                input.setPassword(password);
                input.setEnv(Env.CLOUD);
                var cert = dosignRemoteSign.certs(input);
                var certFactory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(cert)));
            } catch (it.doqui.dosign.dosign.business.session.dosign.remotev2.DosignException_Exception e) {
                logDosignException(e);
                throw new DocumentOperationException(e);
            } catch (CertificateException e) {
                log.error("Error during conversion of certificate: {}{}", e.getMessage(), e.getCause() != null && e.getCause().getMessage() != null ? ": " + e.getCause().getMessage() : "");
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private void doUserCertificateExpiringVerification(long t0, X509Certificate userCert) throws DocumentOperationException {
        if (userCert != null) {
            if (t0 < userCert.getNotBefore().toInstant().toEpochMilli()) {
                log.error("ERROR VALIDATION CERTIFICATE: user certificate not yet valid");
                throw new DocumentOperationException("ERROR VALIDATION CERTIFICATE: user certificate not yet valid");
            }
            long expirationTS = userCert.getNotAfter().toInstant().toEpochMilli();
            if (t0 > expirationTS) {
                log.error("ERROR VALIDATION CERTIFICATE: user certificate expired");
                throw new DocumentOperationException("ERROR VALIDATION CERTIFICATE: user certificate expired");
            }
            if (t0 >= expirationTS - userCertificateExpiring.toMillis()) {
                log.warn("WARNING VALIDATION CERTIFICATE: user certificate near to expiration: {}", userCert.getNotAfter());
                throw new DocumentOperationException("WARNING VALIDATION CERTIFICATE: user certificate near to expiration: " + userCert.getNotAfter());
            }
            log.debug("Valid user certificate");
        }
    }

    private void doPostVerification(ActiveNode node, long operationBegin, X509Certificate userCert) {
        log.debug("Verification required. Re-submitting document to DoSign...");
        doDocOperation(() -> {
            var c = node.getContent(ContentReferenceable.of(CM_CONTENT)).orElseThrow(PreconditionFailedException::new);
            var nodeAttachment = contentRetriever.attachment(node, c);
            var verifyResponse = verifyDocumentSync(nodeAttachment, null, vr ->
                doDocOperation(() -> {
                    if (vr.getSignature().length < 1) {
                        throw new DocumentOperationException("Document not signed!");
                    }
                    var signature = Arrays.stream(vr.getSignature())
                        .filter(sign -> sign.getDataOra().getTime() / 1000 >= operationBegin)
                        .findFirst()
                        .orElseThrow(() -> new DocumentOperationException("Something went wrong on signing operation"));
                    if (userCert != null) {
                        doUserVerification(userCert, signature);
                    }
                    return null;
                })
            );
            if (verifyResponse.getStatus().equals(DocumentOperationResponse.SignOperationStatus.NULL)) {
                throw new DocumentOperationException("VerifyReport of signed document is null");
            }
            return null;
        });
        log.debug("Verification completed: document signed correctly");
    }

    private void doUserVerification(X509Certificate userCert, it.doqui.index.ecmengine.mtom.dto.Signature signature) throws DocumentOperationException {
        log.debug("User verification required");
        var certParts = Optional.of(Arrays.stream(userCert.getSubjectX500Principal().getName().split(",")).toList()).orElse(new ArrayList<>());

        if (!certParts.isEmpty()) {
            var serialNumber = ObjectUtils.takeElem(certParts, "serialNumber");
            if (serialNumber != null && !serialNumber.isBlank() && Strings.CS.equals(serialNumber, signature.getSerialNumber())) {
                log.debug("serialNumber matches, user verified");
                return;
            }
//            var dn = ObjectUtils.takeElem(certParts, "dnQualifier");
//            if (dn != null && !dn.isBlank() && Strings.CS.equals(dn, signature.getDnQualifier())) {
//                log.debug("dnQualifier matches, user verified");
//                return;
//            }
            var cn = ObjectUtils.takeElem(certParts, "CN");
            if (cn != null && !cn.isBlank() && (Strings.CS.equals(cn, signature.getNominativoFirmatario()) || Strings.CS.equals(cn, signature.getFirmatario()))) {
                log.debug("Name matches, user verified");
                return;
            }
        }
        throw new DocumentOperationException("VERIFY CERTIFICATE ERROR: User does not match with signer");
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

    private it.doqui.dosign.dosign.business.session.dosign.defered.SigilloSignatureDto convertParams(SealParams params) {
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

    private SignType mapSealType(String sealType) {
        if (Strings.CS.equals(sealType, "PDF")) {
            return SignType.PADES;
        }
        if (Strings.CS.startsWith(sealType, "XML")) {
            return SignType.XADES;
        }
        return SignType.CADES;
    }

    private Format mapSignTypeToFormat(SignType signType) {
        return switch (signType) {
            case PADES -> Format.PADES;
            case XADES -> Format.XADES_ENVELOPED;
            default -> Format.CADES;
        };
    }

    private boolean verifyTsCredentials(SignParams params) {
        return params.getTsPassword() != null && params.getTsUsername() != null
            && !params.getTsPassword().isEmpty() && !params.getTsUsername().isEmpty();
    }

    private void checkSignTypeWithMimetype(String mimetype, SignType signType) {
        if (signType.name().equals(SignType.PADES.name()) && !mimetype.equals(MIMETYPE_PDF)) {
            throw new PreconditionFailedException("Trying to sign PAdES a non-PDF document.");
        }
        if (signType.name().equals(SignType.XADES.name()) && !mimetype.equals(MIMETYPE_TEXT_XML) && !mimetype.equals(MIMETYPE_APP_XML)) {
            throw new PreconditionFailedException("Trying to sign XAdES a non-XML document.");
        }
    }

    private SignData createSignData(DocumentOperation operation, SignType signType, Provider provider, String username) {
        var signData = new SignData();
        signData.setSignedAt(ZonedDateTime.now());
        signData.setIdentity(sessionContext.getUserIdentity());
        signData.setAuthority(sessionContext.getUserContext().getAuthority());
        signData.setSignType(signType);
        signData.setProvider(provider);
        signData.setUsername(username);
        signData.setDocumentOperation(operation);
        return signData;
    }

    private String mapSignTypeToMimetype(SignType signType) {
        if (signType == null) {
            return GENERIC_MIMETYPE;
        }
        return switch (signType) {
            case PADES -> MIMETYPE_PDF;
            case XADES -> MIMETYPE_APP_XML;
            case CADES -> MIMETYPE_P7M;
        };
    }

    private String createProposedName(String name, DocumentOperation documentOperation, SignType signType) {
        var attr = (documentOperation.equals(DocumentOperation.SIGN) ? "_signed" : (documentOperation.equals(DocumentOperation.SEAL) ? "_sealed" : ""));
        var dot = name.lastIndexOf(".");
        var proposedExt = dot > 0 ? name.substring(dot) : proposeExt(signType);
        if (!name.contains("_sealed") && !name.contains("_signed")) {
            if (dot > 0) {
                name = name.substring(0, dot) + attr + proposedExt;
            } else {
                name = name + attr + proposedExt;
            }
        } else if (dot < 0) {
            name = name + proposedExt;
        }
        return name + (signType != null && signType.equals(SignType.CADES) ? "." + EXT_CADES : "");
    }

    private String proposeExt(SignType signType) {
        if (signType == null) {
            return "";
        }
        return switch (signType) {
            case XADES -> "." + EXT_XML;
            case PADES -> "." + EXT_PDF;
            default -> "";
        };
    }

    private void doDocOperation(Callable<Void> task) {
        try {
            task.call();
        } catch (Throwable e) {
            logDosignException(e);
            throw new SystemException(e);
        }
    }

    protected void logDosignException(Throwable e) {
        log.error("Dosign has thrown exception: {}{}", e.getMessage(), e.getCause() != null && e.getCause().getMessage() != null ? ": " + e.getCause().getMessage() : "");
    }
}

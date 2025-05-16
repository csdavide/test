package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.CertBuffer;
import it.doqui.index.ecmengine.mtom.dto.Content;
import it.doqui.index.ecmengine.mtom.dto.DigestInfo;
import it.doqui.libra.librabl.api.v1.cxf.impl.AsyncOpServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.ContentServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.DocumentServiceBridge;
import it.doqui.libra.librabl.api.v1.cxf.impl.MimeTypeServiceBridge;
import it.doqui.libra.librabl.api.v1.rest.components.interfaces.UtilsBusinessInterface;
import it.doqui.libra.librabl.api.v1.rest.dto.*;
import it.doqui.libra.librabl.foundation.flow.BusinessComponent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.util.Optional;

@ApplicationScoped
@Slf4j
public class UtilsBusinessComponent extends AbstractBusinessComponent implements BusinessComponent {

    @Inject
    DocumentServiceBridge documentService;

    @Inject
    AsyncOpServiceBridge asyncOpService;

    @Inject
    ContentServiceBridge contentService;

    @Inject
    MimeTypeServiceBridge mimeTypeService;

    @Override
    public Class<?> getComponentInterface() {
        return UtilsBusinessInterface.class;
    }

    public AsyncReport getAsyncReport(String jobId) {
        return dtoMapper.convert(documentService.getAsyncReport(jobId), AsyncReport.class);
    }

    public AsyncSigillo getAsyncSigillo(String jobId) {
        return dtoMapper.convert(documentService.getAsyncSigillo(jobId, null), AsyncSigillo.class);
    }

    public Job getServiceJobInfo(String jobId) {
        return dtoMapper.convert(asyncOpService.getServiceJobInfo(jobId, null), Job.class);
    }

    public FileFormatInfo[] identifyDocument(byte[] bytes, Boolean store) {
        return map(contentService.identifyDocument(document(bytes, store), null));
    }

    public String getSignatureType(byte[] bytes) {
        var content = new Content();
        content.setContent(bytes);
        return documentService.getSignatureType(new it.doqui.index.ecmengine.mtom.dto.Node(), content, null);
    }

    public VerifyCertificateReport verifyCertificate(VerifyParameter verifyParameter, byte[] bytes, Boolean store) {
        var buffer = new CertBuffer();
        buffer.setData(bytes);
        buffer.setStore(BooleanUtils.toBoolean(store));

        var vp = dtoMapper.convert(verifyParameter, it.doqui.index.ecmengine.mtom.dto.VerifyParameter.class);

        var result = documentService.verifyCertificate(buffer, vp, null);
        return dtoMapper.convert(result, VerifyCertificateReport.class);
    }

    public String extractFromEnvelope(byte[] bytes) {
        var result = documentService.extractDocumentFromEnvelopeEnvelopedDocument(document(bytes, true), null);
        return result.getUid();
    }

    public String generateDigestFromContent(byte[] bytes, String algorithm) {
        var content = new Content();
        content.setContent(bytes);

        var digestInfo = new DigestInfo();
        digestInfo.setAlgorithm(algorithm);

        var result = documentService.generateDigestFromContent(content, digestInfo, null);
        return result.getDigest();
    }

    public VerifyReport verifySignedDocument(
        byte[] documentBinaryContent, String documentUid,
        String documentContentPropertyName, String documentStore, byte[] detachedSignatureBinaryContent,
        String detachedSignatureUid, String detachedSignatureContentPropertyName, String detachedSignatureStore,
        VerifyParameter verifyParameter) {
        var document = document(
            documentBinaryContent, documentUid, documentContentPropertyName,
            Optional.ofNullable(documentStore).map(BooleanUtils::toBoolean)
                .orElse(null)
        );

        var documentSignature = documentIfAny(
            detachedSignatureBinaryContent, detachedSignatureUid, detachedSignatureContentPropertyName,
            Optional.ofNullable(detachedSignatureStore).map(BooleanUtils::toBoolean)
                .orElse(null)
        );

        var vp = dtoMapper.convert(verifyParameter, it.doqui.index.ecmengine.mtom.dto.VerifyParameter.class);
        var result = documentService.verifySignedDocument(document, documentSignature, vp, null);
        return dtoMapper.convert(result, VerifyReport.class);
    }

    public VerifyReportExt verifyDocumentExt(
        byte[] documentBinaryContent, String documentUid,
        String documentContentPropertyName, String documentStore, byte[] detachedSignatureBinaryContent,
        String detachedSignatureUid, String detachedSignatureContentPropertyName, String detachedSignatureStore,
        VerifyParameter verifyParameter) {
        var document = document(
            documentBinaryContent, documentUid, documentContentPropertyName,
            Optional.ofNullable(detachedSignatureStore).map(BooleanUtils::toBoolean)
                .orElse(null)
        );

        var documentSignature = documentIfAny(
            detachedSignatureBinaryContent, detachedSignatureUid, detachedSignatureContentPropertyName,
            Optional.ofNullable(documentStore).map(BooleanUtils::toBoolean)
                .orElse(null)
        );

        var vp = dtoMapper.convert(verifyParameter, it.doqui.index.ecmengine.mtom.dto.VerifyParameter.class);
        var result = documentService.verifyDocumentExt(document, documentSignature, vp, null);
        return dtoMapper.convert(result, VerifyReportExt.class);
    }

    public String verifyAsyncDocument(
        byte[] documentBinaryContent, String documentUid, String documentContentPropertyName,
        String documentStore, byte[] detachedSignatureBinaryContent, String detachedSignatureUid,
        String detachedSignatureContentPropertyName, String detachedSignatureStore, VerifyParameter verifyParameter) {
        var document = document(
            documentBinaryContent, documentUid, documentContentPropertyName,
            Optional.ofNullable(detachedSignatureStore).map(BooleanUtils::toBoolean)
                .orElse(null)
        );

        var documentSignature = documentIfAny(
            detachedSignatureBinaryContent, detachedSignatureUid, detachedSignatureContentPropertyName,
            Optional.ofNullable(documentStore).map(BooleanUtils::toBoolean)
                .orElse(null)
        );

        var vp = dtoMapper.convert(verifyParameter, it.doqui.index.ecmengine.mtom.dto.VerifyParameter.class);
        return documentService.verifyAsyncDocument(document, documentSignature, vp, null);
    }

    public SigilloSignedExt sigilloSignatureExt(
        byte[] documentBinaryContent, String documentUid,
        String documentContentPropertyName, SigilloSigner sigilloSigner) {
        var document = document(documentBinaryContent, documentUid, documentContentPropertyName, true);
        var signer = dtoMapper.convert(sigilloSigner, it.doqui.index.ecmengine.mtom.dto.SigilloSigner.class);
        var result = documentService.sigilloSignatureExt(document, signer, null);
        return dtoMapper.convert(result, SigilloSignedExt.class);
    }

    public Mimetype[] getMimetype(String fileExtension, String mimetype) {
        var input = new it.doqui.index.ecmengine.mtom.dto.Mimetype();
        input.setFileExtension(fileExtension);
        input.setMimetype(mimetype);
        var output = mimeTypeService.getMimetype(input);
        var result = new Mimetype[output.length];
        for (int i = 0; i < output.length; i++) {
            var mt = new Mimetype();
            mt.setFileExtension(output[i].getFileExtension());
            mt.setMimetype(output[i].getMimetype());
            result[i] = mt;
        }
        return result;
    }
}

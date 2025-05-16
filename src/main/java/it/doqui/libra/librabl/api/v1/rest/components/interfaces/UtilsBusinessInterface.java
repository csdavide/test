package it.doqui.libra.librabl.api.v1.rest.components.interfaces;

import it.doqui.libra.librabl.api.v1.rest.dto.*;

public interface UtilsBusinessInterface {

    // done
    FileFormatInfo[] identifyDocument(byte[] bytes, Boolean store);

    // done
    AsyncReport getAsyncReport(String jobId);

    // done
    VerifyCertificateReport verifyCertificate(VerifyParameter verifyParameter, byte[] bytes, Boolean store)
	   ;

    // done
    String extractFromEnvelope(byte[] bytes);

    @Deprecated
    FileFormatInfo[] getFileFormatInfo(String fileName, byte[] bytes);

    Mimetype[] getMimetype(String fileExtension, String mimetype);

    // done
    Job getServiceJobInfo(String jobId);

    // done
    String generateDigestFromContent(byte[] bytes, String algorithm);

    // done
    VerifyReport verifySignedDocument(byte[] documentBinaryContent, String documentUid,
	    String documentContentPropertyName, String documentStore, byte[] detachedSignatureBinaryContent,
	    String detachedSignatureUid, String detachedSignatureContentPropertyName, String detachedSignatureStore,
	    VerifyParameter verifyParameter);

    // done
    VerifyReportExt verifyDocumentExt(byte[] documentBinaryContent, String documentUid,
	    String documentContentPropertyName, String documentStore, byte[] detachedSignatureBinaryContent,
	    String detachedSignatureUid, String detachedSignatureContentPropertyName, String detachedSignatureStore,
	    VerifyParameter verifyParameter);

    // done
    String verifyAsyncDocument(byte[] documentBinaryContent, String documentUid, String documentContentPropertyName,
	    String documentStore, byte[] detachedSignatureBinaryContent, String detachedSignatureUid,
	    String detachedSignatureContentPropertyName, String detachedSignatureStore, VerifyParameter verifyParameter)
	   ;

    // done
    String getSignatureType(byte[] bytes);

    // done
    AsyncSigillo getAsyncSigillo(String jobId);

    // done
    SigilloSignedExt sigilloSignatureExt(byte[] documentBinaryContent, String documentUid,
	    String documentContentPropertyName, SigilloSigner sigilloSigner);

}
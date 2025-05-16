package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.business.service.document.DocumentStream;
import it.doqui.libra.librabl.business.service.exceptions.SignOperationException;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.document.*;
import it.doqui.libra.librabl.views.node.ContentRef;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;

public interface DocumentService {
    ContentRef unwrapIntoNode(ContentRef contentRef) throws IOException, SignOperationException;
    DocumentStream unwrap(ContentRef contentRef) throws IOException, SignOperationException;
    DocumentStream unwrap(InputStream stream) throws IOException, SignOperationException;
    DigestResponse digest(ContentRef contentRef, boolean enveloped, String algorithm) throws NoSuchAlgorithmException, IOException;
    DigestResponse digest(InputStream stream, String algorithm) throws NoSuchAlgorithmException, IOException;
    DocumentSignOperationResponse verifyDocument(ContentRef documentRef, ContentRef detachedDocumentRef, ZonedDateTime verifyAt, Duration timeout, OperationMode mode) throws IOException, SignOperationException;
    DocumentSignOperationResponse getVerificationReport(String requestId) throws SignOperationException;
    DocumentSignOperationResponse sealDocument(ContentRef documentRef, SealParams params, Duration timeout, OperationMode mode) throws SignOperationException, IOException;
    DocumentSignOperationResponse getSealingReport(String requestId) throws SignOperationException;
    DocumentSignOperationResponse verifyCertificate(DocumentStream certStream, CertificateParams params) throws SignOperationException, IOException;
    ErrorReport getErrorReport(String tokenUid, SignOperation type) throws SignOperationException;
}

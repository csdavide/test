package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.document.DocumentOperation;
import it.doqui.libra.librabl.domain.model.document.DocumentStream;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.domain.model.exceptions.DocumentOperationException;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.application.model.document.*;
import it.doqui.libra.librabl.domain.model.files.ContentRef;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;

public interface DocumentUseCase {
    ContentRef unwrapIntoNode(ContentRef contentRef) throws IOException, DocumentOperationException;
    DocumentStream unwrap(ContentRef contentRef) throws IOException, DocumentOperationException;
    DocumentStream unwrap(InputStream stream) throws IOException, DocumentOperationException;
    DigestResponse digest(ContentRef contentRef, boolean enveloped, String algorithm) throws NoSuchAlgorithmException, IOException;
    DigestResponse digest(InputStream stream, String algorithm) throws NoSuchAlgorithmException, IOException;
    DocumentOperationResponse verifyDocument(ContentRef documentRef, ContentRef detachedDocumentRef, ZonedDateTime verifyAt, Duration timeout, OperationMode mode) throws IOException, DocumentOperationException;
    DocumentOperationResponse getVerificationReport(String requestId) throws DocumentOperationException;
    DocumentOperationResponse sealDocument(ContentRef documentRef, SealParams params, StoreParams storeParams, Duration timeout, OperationMode mode) throws DocumentOperationException, IOException;
    DocumentOperationResponse getSealingReport(String requestId) throws DocumentOperationException;
    DocumentOperationResponse verifyCertificate(DocumentStream certStream, CertificateParams params) throws DocumentOperationException, IOException;
    ErrorReport getErrorReport(String tokenUid, DocumentOperation type) throws DocumentOperationException;
    void pushOtp(OTPRequest otpRequest) throws DocumentOperationException;
    DocumentOperationResponse signDocument(ContentRef contentRef, SignParams params, StoreParams storeParams) throws DocumentOperationException, IOException;
    AsyncOperation<?> submitSignDocument(ContentRef contentRef, SignParams params, StoreParams storeParams);
}

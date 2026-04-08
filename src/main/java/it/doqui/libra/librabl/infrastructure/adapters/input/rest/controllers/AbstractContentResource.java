package it.doqui.libra.librabl.infrastructure.adapters.input.rest.controllers;

import it.doqui.libra.librabl.application.model.document.CertificateParams;
import it.doqui.libra.librabl.application.ports.in.ContentUseCase;
import it.doqui.libra.librabl.application.ports.in.DocumentAnalyzeUseCase;
import it.doqui.libra.librabl.application.ports.in.DocumentUseCase;
import it.doqui.libra.librabl.domain.model.document.DocumentStream;
import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.service.MimeTypeService;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document.SignRequest;
import it.doqui.libra.librabl.utils.IOUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.entity.ContentType;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;

import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Slf4j
public abstract class AbstractContentResource extends AbstractResource {

    @Inject
    protected ContentUseCase contentUseCase;

    @Inject
    protected DocumentUseCase documentService;

    @Inject
    protected DocumentAnalyzeUseCase documentAnalyzer;

    @Inject
    protected MimeTypeService mimeTypeService;

    protected ContentStream makeContentStream(String contentPropertyName, String contentType, String contentDisposition, InputStream is) {
        var ct = ContentType.parse(contentType);
        var cs = new ContentStream();
        cs.setName(contentPropertyName);
        cs.setMimetype(ct.getMimeType());
        cs.setEncoding(Optional.ofNullable(ct.getCharset()).map(Charset::toString).orElse(null));
        cs.setFileName(IOUtils.getFileName(contentDisposition));
        cs.setInputStream(is);

        return cs;
    }

    protected Response getNodeContent(String uuid, String contentPropertyName, boolean inline, String fileName) {
        return call(() -> {
            var ref = new ContentRef().setUuid(uuid).setContentPropertyName(contentPropertyName).setFileName(fileName);
            NodeAttachment a = contentUseCase.getNodeContent(ref);
            if (StringUtils.isBlank(a.getContentProperty().getMimetype()) || Strings.CI.equals(a.getContentProperty().getMimetype(), APPLICATION_OCTET_STREAM)) {
                Optional.ofNullable(mimeTypeService.getMimeType(a.getName())).ifPresent(mt -> a.getContentProperty().setMimetype(mt));
            }

            var mimeType = IOUtils.mimeType(a.getContentProperty().getMimetype());
            return Response.ok(a.getFile())
                .type(mimeType)
                .header("Content-Disposition", a.formatDisposition(inline))
                .build();
        });
    }

    protected Response getContentFormat(String uuid, String contentPropertyName, String fileName) {
        return call(() -> Response.ok(documentAnalyzer.getFileFormat(contentRef(uuid,contentPropertyName,fileName))).build());
    }

    protected Response unwrap(String uuid, String contentPropertyName, String fileName) {
        return call(() -> Response.ok(documentService.unwrapIntoNode(contentRef(uuid,contentPropertyName,fileName))).build());
    }

    protected Response digest(String uuid, String contentPropertyName, String fileName, boolean enveloped, String alg) {
        return call(() -> Response.ok(documentService.digest(contentRef(uuid,contentPropertyName,fileName), enveloped, alg)).build());
    }

    protected Response verifyCertificate(String uuid, String contentPropertyName, String fileName, CertificateParams params) {
        return call(() -> {
            DocumentStream input = mapAsDocumentStream(uuid, contentPropertyName, fileName);
            return Response.ok(documentService.verifyCertificate(input, params)).build();
        });
    }

    protected ContentRef contentRef(String uuid, String contentPropertyName, String fileName) {
        var contentRef = new ContentRef();
        contentRef.setUuid(uuid);
        contentRef.setContentPropertyName(contentPropertyName);
        contentRef.setFileName(fileName);
        return contentRef;
    }

    protected Response signDocument(String uuid, String contentPropertyName, String fileName, SignRequest signRequest) {
        return call(() -> {
            if (signRequest.getMode().equals(OperationMode.SYNC)) {
                return Response.ok(
                    documentService.signDocument(
                        contentRef(uuid, contentPropertyName, fileName),
                        signRequest.getSignParams(),
                        signRequest.getStoreParams()
                    )
                ).build();
            } else {
                var op = documentService.submitSignDocument(signRequest.getDocument(), signRequest.getSignParams(), signRequest.getStoreParams());
                return Response.accepted(op).build();
            }
        });
    }

    private DocumentStream mapAsDocumentStream(String uuid, String contentPropertyName, String fileName) {
        try {
            var attachment = contentUseCase.getNodeContent(contentRef(uuid, contentPropertyName, fileName));
            var documentStream = new DocumentStream();
            documentStream.setInputStream(new FileInputStream(attachment.getFile()));
            documentStream.setFileName(attachment.getName());
            return documentStream;
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }
}

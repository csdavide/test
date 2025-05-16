package it.doqui.libra.librabl.api.v2.rest.controllers;

import it.doqui.libra.librabl.api.core.AbstractResource;
import it.doqui.libra.librabl.business.service.document.DocumentStream;
import it.doqui.libra.librabl.business.service.interfaces.DocumentAnalyzer;
import it.doqui.libra.librabl.business.service.interfaces.DocumentService;
import it.doqui.libra.librabl.business.service.interfaces.NodeContentService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.views.document.CertificateParams;
import it.doqui.libra.librabl.views.node.ContentRef;
import it.doqui.libra.librabl.views.node.ContentStream;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;

@Slf4j
public class AbstractContentResource extends AbstractResource {

    @Inject
    protected NodeContentService nodeContentService;

    @Inject
    protected DocumentService documentService;

    @Inject
    protected DocumentAnalyzer documentAnalyzer;

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
            NodeAttachment a = nodeContentService.getNodeContent(uuid, contentPropertyName, fileName);
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

    private DocumentStream mapAsDocumentStream(String uuid, String contentPropertyName, String fileName) {
        var attachment = nodeContentService.getNodeContent(contentRef(uuid, contentPropertyName, fileName));

        try {
            var documentStream = new DocumentStream();
            documentStream.setInputStream(new FileInputStream(attachment.getFile()));
            documentStream.setFileName(attachment.getName());
            return documentStream;
        } catch (FileNotFoundException e) {
            throw new SystemException(e);
        }
    }
}

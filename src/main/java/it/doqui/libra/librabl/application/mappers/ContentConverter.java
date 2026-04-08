package it.doqui.libra.librabl.application.mappers;

import it.doqui.libra.librabl.domain.service.MimeTypeService;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.IOUtils;
import it.doqui.libra.librabl.application.model.properties.ConverterType;
import it.doqui.libra.librabl.domain.model.files.ContentStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@ApplicationScoped
public class ContentConverter {

    @Inject
    MimeTypeService mimeTypeService;

    public ContentStream convert(ContentStream cs, ConverterType converter) {
        if (converter == ConverterType.IDENTITY) {
            return cs;
        }

        var s = convert(new InStream(cs.getInputStream(), cs.getMimetype()), converter);
        var out = new ContentStream();
        out.copyFrom(cs);
        out.setSize(cs.getSize());
        out.setInputStream(s.stream());
        out.setMimetype(s.mimetype());

        if (StringUtils.isNotBlank(out.getFileName())) {
            out.setFileName(mimeTypeService.filename(out.getFileName(), s.mimetype()));
        }

        return out;
    }

    public InStream convert(InStream in, ConverterType converter) {
        if (converter == ConverterType.IDENTITY) {
            return in;
        }

        try (PDDocument document = new PDDocument()) {
            // Crea un oggetto immagine PDF dalla BufferedImage
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, IOUtils.readFully(in.stream()), null);

            // Crea una pagina con le dimensioni dell'immagine
            PDPage page = new PDPage(new PDRectangle(pdImage.getWidth(), pdImage.getHeight()));
            document.addPage(page);

            // Disegna l'immagine sulla pagina
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, 0, 0, pdImage.getWidth(), pdImage.getHeight());
            }

            // Salva il documento
            try (var outputStream = new ByteArrayOutputStream()) {
                document.save(outputStream);
                return new InStream(new ByteArrayInputStream(outputStream.toByteArray()), "application/pdf");
            }
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    public record InStream(InputStream stream, String mimetype) {}
}

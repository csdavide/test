package it.doqui.libra.librabl.infrastructure.adapters.output.rendition;

import it.doqui.libra.librabl.application.model.rendition.XSLTTransformer;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

@ApplicationScoped
@Slf4j
public class XSLTFOPTransformer implements XSLTTransformer {

    @ConfigProperty(name = "libra.apache-fop.configuration.path", defaultValue = "./res/cfg_fop.xconf")
    String fopConfigPath;

    @Override
    public String getDefaultMimeType() {
        return MimeConstants.MIME_PDF;
    }

    @Override
    public byte[] transform(InputStream rtStream, InputStream rdStream, String mimeType) {
        try (var outputStream = new ByteArrayOutputStream()) {
            log.trace("Mimetype '{}' was requested", mimeType);

            if (Strings.CI.equals("text/xml", mimeType) || Strings.CI.equals("application/xml", mimeType)) {
                mimeType = MimeConstants.MIME_FOP_AREA_TREE;
            } else if (isMimetypeNotSupported(mimeType)) {
                mimeType = getDefaultMimeType();
            }
            log.trace("Mimetype '{}' will be used", mimeType);

            var fopFactory = FopFactory.newInstance(new File(fopConfigPath));
            log.debug("FopFactory instantiated correctly.");

            var fop = fopFactory.newFop(mimeType, outputStream);
            log.debug("Fop instantiated correctly.");

            var src = new StreamSource(rdStream);
            var rtSrc = new StreamSource(rtStream);
            var res = new SAXResult(fop.getDefaultHandler());

            @SuppressWarnings("java:S2755")
            var transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
//			transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); // Compliant... No, non viene riconosciuta
            transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); // Compliant
            log.debug("TransformerFactory instantiated correctly.");
            var transformer = transformerFactory.newTransformer(rtSrc);
            log.debug("Transformer instantiated correctly.");
            transformer.transform(src, res);

            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error during FOP transformation: {}", e.getMessage());
            throw new WebException(500, "Error during transformation: " + e.getMessage());
        }
    }

    private boolean isMimetypeNotSupported(String mimeType) {
        return !Strings.CI.equals(mimeType, MimeConstants.MIME_PDF)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_FOP_AWT_PREVIEW)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_FOP_PRINT)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_FOP_AREA_TREE)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_FOP_IF)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_PNG)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_TIFF)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_PLAIN_TEXT)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_POSTSCRIPT)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_PCL)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_PCL_ALT)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_AFP)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_AFP_ALT)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_RTF)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_RTF_ALT1)
            && !Strings.CI.equals(mimeType, MimeConstants.MIME_RTF_ALT2);
    }
//    @Override
//    public byte[] transform(byte[] xml, byte[] xsl, String mimeType) {
//        try (var outputStream = new ByteArrayOutputStream()) {
//            log.trace("Mimetype '{}' was requested", mimeType);
//
//            String finalMimetype = mimeType != null && (
//                Strings.CI.equals(MimeConstants.MIME_PDF, mimeType)
//                    || Strings.CI.equals(MimeConstants.MIME_PLAIN_TEXT, mimeType)
//                    || Strings.CI.equals(MimeConstants.MIME_PCL, mimeType)
//                    || Strings.CI.equals(MimeConstants.MIME_POSTSCRIPT, mimeType)
//                    || Strings.CI.equals("text/xml", mimeType)
//                    || Strings.CI.equals("application/x-frame", mimeType)
//            ) ? mimeType : getDefaultMimeType();
//            log.trace("Mimetype '{}' will be used", finalMimetype);
//
//            Fop fop = FopFactory.newInstance(new File(fopConfigPath)).newFop(finalMimetype, outputStream);
//
//            Source src = new StreamSource(new ByteArrayInputStream(xml));
//            Result res = new SAXResult(fop.getDefaultHandler());
//
//            Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new ByteArrayInputStream(xsl)));
//            transformer.transform(src, res);
//
//            return outputStream.toByteArray();
//        } catch (Exception e) {
//            log.error("Error during FOP transformation: {}", e.getMessage());
//            throw new WebException(500, "Error during transformation: " + e.getMessage());
//        }
//    }
}

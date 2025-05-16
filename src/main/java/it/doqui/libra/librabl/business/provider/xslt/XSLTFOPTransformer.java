package it.doqui.libra.librabl.business.provider.xslt;

import it.doqui.libra.librabl.foundation.exceptions.WebException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;

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

            if (StringUtils.equalsIgnoreCase("text/xml", mimeType) || StringUtils.equalsIgnoreCase("application/xml", mimeType)) {
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

            var transformerFactory = TransformerFactory.newInstance();
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
        return !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_PDF)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_FOP_AWT_PREVIEW)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_FOP_PRINT)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_FOP_AREA_TREE)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_FOP_IF)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_PNG)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_TIFF)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_PLAIN_TEXT)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_POSTSCRIPT)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_PCL)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_PCL_ALT)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_AFP)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_AFP_ALT)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_RTF)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_RTF_ALT1)
            && !StringUtils.equalsIgnoreCase(mimeType, MimeConstants.MIME_RTF_ALT2);
    }
//    @Override
//    public byte[] transform(byte[] xml, byte[] xsl, String mimeType) {
//        try (var outputStream = new ByteArrayOutputStream()) {
//            log.trace("Mimetype '{}' was requested", mimeType);
//
//            String finalMimetype = mimeType != null && (
//                StringUtils.equalsIgnoreCase(MimeConstants.MIME_PDF, mimeType)
//                    || StringUtils.equalsIgnoreCase(MimeConstants.MIME_PLAIN_TEXT, mimeType)
//                    || StringUtils.equalsIgnoreCase(MimeConstants.MIME_PCL, mimeType)
//                    || StringUtils.equalsIgnoreCase(MimeConstants.MIME_POSTSCRIPT, mimeType)
//                    || StringUtils.equalsIgnoreCase("text/xml", mimeType)
//                    || StringUtils.equalsIgnoreCase("application/x-frame", mimeType)
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

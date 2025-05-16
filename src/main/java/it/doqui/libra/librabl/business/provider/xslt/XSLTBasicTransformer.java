package it.doqui.libra.librabl.business.provider.xslt;

import it.doqui.libra.librabl.foundation.exceptions.WebException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

@ApplicationScoped
@Slf4j
public class XSLTBasicTransformer implements XSLTTransformer, ErrorListener {
    @Override
    public String getDefaultMimeType() {
        return "text/html";
    }

//    @Override
//    public byte[] transform(byte[] xml, byte[] xsl, String mimetype) {
//        try (var outputStream = new ByteArrayOutputStream()) {
//
//            Source xmlSource = new StreamSource(new ByteArrayInputStream(xml));
//            Source xslSource = new StreamSource(new ByteArrayInputStream(xsl));
//            Result result = new StreamResult(outputStream);
//
//            Transformer transformer = TransformerFactory.newInstance().newTransformer(xslSource);
//            transformer.setErrorListener(this);
//
//            transformer.transform(xmlSource, result);
//
//            return outputStream.toByteArray();
//
//        } catch (Exception e) {
//            log.error("Error during transformation: {}", e.getMessage());
//            throw new WebException(500, "Error during transformation: " + e.getMessage());
//
//        }
//    }

    @Override
    public byte[] transform(InputStream rtStream, InputStream rdStream, String mimeType) {
        try (var outputStream = new ByteArrayOutputStream()) {

            Source xmlSource = new StreamSource(rdStream);
            Source xslSource = new StreamSource(rtStream);
            Result result = new StreamResult(outputStream);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            log.debug("TransformerFactory instantiated correctly.");
            Transformer transformer = transformerFactory.newTransformer(xslSource);
            log.debug("Transformer instantiated correctly.");
            transformer.setErrorListener(this);

            transformer.transform(xmlSource, result);

            return outputStream.toByteArray();
        }
        catch (Exception e) {
            log.error("Error during transformation: {}", e.getMessage());
            throw new WebException(500, "Error during transformation: " + e.getMessage());
        }
    }

    @Override
    public void warning(TransformerException e) throws TransformerException {
        log.warn("[XSLTBasicTransformer::warning] Transform warning: {}", e.getLocalizedMessage());
    }

    @Override
    public void error(TransformerException e) throws TransformerException {
        log.error("[XSLTBasicTransformer::error] Transform error: {}", e.getLocalizedMessage());
        throw e;
    }

    @Override
    public void fatalError(TransformerException e) throws TransformerException {
        log.error("[XSLTBasicTransformer::fatalError] Transform fatal error: {}", e.getLocalizedMessage());
        throw e;
    }
}

package it.doqui.libra.librabl.infrastructure.adapters.output.rendition;

import it.doqui.libra.librabl.application.model.rendition.XSLTTransformer;
import it.doqui.libra.librabl.application.ports.out.XSLTFactoryPort;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@ApplicationScoped
public class XSLTFactory implements XSLTFactoryPort {

    @Inject
    XSLTBasicTransformer basicTransformer;

    @Inject
    XSLTFOPTransformer fopTransformer;

    @Override
    public XSLTTransformer getXSLT(File f) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            final DocumentBuilder builder = dbf.newDocumentBuilder();
            final Document doc = builder.parse(f);
            return getXSLT(doc);

        } catch (Exception e) {
            throw new RuntimeException("Unable to find XSLT", e);
        }
    }

    @Override
    public XSLTTransformer getXSLT(byte[] buffer) {
        try (ByteArrayInputStream is = new ByteArrayInputStream(buffer)) {
            return getXSLT(is);
        } catch (IOException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public XSLTTransformer getXSLT(InputStream is) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            final DocumentBuilder builder = dbf.newDocumentBuilder();
            final Document doc = builder.parse(is);
            return getXSLT(doc);

        } catch (Exception e) {
            throw new RuntimeException("Unable to find XSLT", e);
        }
    }

    private XSLTTransformer getXSLT(Document doc) {
        final NodeList nlist = doc.getElementsByTagName("xsl:stylesheet");
        final Node stylesheet = nlist.item(0);
        final NamedNodeMap nnm = stylesheet.getAttributes();
        for (int x = 0; x < nnm.getLength(); x++) {
            if (nnm.item(x).getNodeName().equalsIgnoreCase("xmlns:fo")) {
                return fopTransformer;
            }
        }
        return basicTransformer;
    }
}

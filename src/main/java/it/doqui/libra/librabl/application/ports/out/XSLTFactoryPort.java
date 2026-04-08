package it.doqui.libra.librabl.application.ports.out;

import it.doqui.libra.librabl.application.model.rendition.XSLTTransformer;

import java.io.File;
import java.io.InputStream;

public interface XSLTFactoryPort {
    XSLTTransformer getXSLT(File f);
    XSLTTransformer getXSLT(byte[] buffer);
    XSLTTransformer getXSLT(InputStream is);
}

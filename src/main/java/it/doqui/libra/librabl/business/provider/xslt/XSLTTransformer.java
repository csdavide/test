package it.doqui.libra.librabl.business.provider.xslt;

import java.io.InputStream;

public interface XSLTTransformer {
    String getDefaultMimeType();
    byte[] transform(InputStream rtStream, InputStream rdStream, String mimeType);
}

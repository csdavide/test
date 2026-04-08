package it.doqui.libra.librabl.application.model.rendition;

import java.io.InputStream;

public interface XSLTTransformer {
    String getDefaultMimeType();
    byte[] transform(InputStream rtStream, InputStream rdStream, String mimeType);
}

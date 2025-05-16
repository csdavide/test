package it.doqui.libra.librabl.business.service.document;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.InputStream;

@Getter
@Setter
@Accessors(chain = true)
public class DocumentStream {
    private InputStream inputStream;
    private String mimeType;
    private String fileName;
    private String description;
}

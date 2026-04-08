package it.doqui.libra.librabl.domain.model.mimetypes;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MimeTypeMapping {
    private Long id;
    private String fileExtension;
    private String mimetype;
    private int priority = 1;
}

package it.doqui.libra.librabl.views.mimetype;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MimeTypeRequest {
    private String fileExtension;
    private String mimetype;

    @JsonSetter(nulls = Nulls.SKIP)
    private int priority = 1;
}

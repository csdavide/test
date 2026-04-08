package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.mimetypes;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString
@Schema(name = "MimeTypeRequest")
public class MimeTypeRequest {
    private String fileExtension;
    private String mimetype;

    @JsonSetter(nulls = Nulls.SKIP)
    private int priority = 1;
}

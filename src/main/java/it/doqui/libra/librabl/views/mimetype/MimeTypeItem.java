package it.doqui.libra.librabl.views.mimetype;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString
@Schema(allOf = MimeTypeRequest.class)
public class MimeTypeItem extends MimeTypeRequest {
    private Long id;
}

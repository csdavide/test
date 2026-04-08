package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.mimetypes;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@ToString
@Schema(name = "MimeTypeItem", allOf = MimeTypeRequest.class)
public class MimeTypeItem extends MimeTypeRequest {
    private Long id;
}

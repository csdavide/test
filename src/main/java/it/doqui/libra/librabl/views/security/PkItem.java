package it.doqui.libra.librabl.views.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = PkRequest.class)
public class PkItem extends PkRequest {
    private String kid;
}

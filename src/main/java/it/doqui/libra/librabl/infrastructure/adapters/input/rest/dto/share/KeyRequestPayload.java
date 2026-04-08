package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.share;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.share.KeyRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyRequestPayload {
    private KeyRequest request;
    private String signature;
}

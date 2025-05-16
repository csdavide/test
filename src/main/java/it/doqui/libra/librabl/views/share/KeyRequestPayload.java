package it.doqui.libra.librabl.views.share;

import com.fasterxml.jackson.annotation.JsonInclude;
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

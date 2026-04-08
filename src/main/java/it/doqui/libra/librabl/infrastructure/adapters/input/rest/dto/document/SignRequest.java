package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.doqui.libra.librabl.application.model.document.SignParams;
import it.doqui.libra.librabl.application.model.document.StoreParams;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignRequest extends DocumentOperationRequest {

    @JsonProperty("params")
    private SignParams signParams;

    @JsonProperty("store")
    private StoreParams storeParams;
}

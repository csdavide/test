package it.doqui.libra.librabl.infrastructure.adapters.input.rest.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.doqui.libra.librabl.application.model.document.SealParams;
import it.doqui.libra.librabl.application.model.document.StoreParams;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@Schema(allOf = DocumentOperationRequest.class)
public class SealRequest extends DocumentOperationRequest {

    @JsonProperty("params")
    private SealParams sealParams;

    @JsonProperty("store")
    private StoreParams storeParams;
}

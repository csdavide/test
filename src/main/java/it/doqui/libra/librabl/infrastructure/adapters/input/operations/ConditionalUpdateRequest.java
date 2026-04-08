package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.graph.InputNodeRequest;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = ConditionalRequest.class)
public class ConditionalUpdateRequest extends ConditionalRequest {
    private InputNodeRequest input;
}

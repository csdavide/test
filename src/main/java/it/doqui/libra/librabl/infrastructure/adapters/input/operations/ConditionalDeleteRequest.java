package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(allOf = ConditionalRequest.class)
public class ConditionalDeleteRequest extends ConditionalRequest {

    @JsonSetter(nulls = Nulls.SKIP)
    private DeleteMode mode = DeleteMode.DELETE;
}

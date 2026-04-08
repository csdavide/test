package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
@Schema(allOf = JobResult.class)
public class ImportResult extends CountResult {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long tx;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<LinkedInputNodeRequest> convertedInputs;

    public ImportResult() {
        this.convertedInputs = new ArrayList<>();
    }
}

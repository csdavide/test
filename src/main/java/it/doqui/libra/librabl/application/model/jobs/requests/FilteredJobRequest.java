package it.doqui.libra.librabl.application.model.jobs.requests;


import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.query.QueryParameters;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class FilteredJobRequest extends JobRequest {
    private QueryParameters query;

    public FilteredJobRequest(String kind) {
        super(kind);
    }
}

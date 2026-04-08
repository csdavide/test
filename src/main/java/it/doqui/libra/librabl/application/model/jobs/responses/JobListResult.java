package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(allOf = JobResult.class)
public class JobListResult extends JobResult implements PartiallyCompletable {
    public JobListResult() {
        super();
        this.setKind("jobs");
    }

    private double completeness;
    private boolean aborted;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<JobResponse> jobs = new ArrayList<>();
}

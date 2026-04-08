package it.doqui.libra.librabl.application.model.jobs.requests;

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
@Schema(allOf = JobRequest.class)
public class JobListRequest extends JobRequest {
    private boolean atomic = true;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<JobRequest> jobs = new ArrayList<>();

    public JobListRequest() {
        super("jobs");
    }
}

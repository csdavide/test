package it.doqui.libra.librabl.application.model.jobs.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobResponse implements AsyncOperation<JobResult> {
    private String jobId;
    private JobStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private String message;
    private JobResult result;
}

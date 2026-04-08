package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
public class JobEntity {
    private String jobId;
    private String tenant;
    private JobStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private JobData data;
}

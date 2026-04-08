package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class AsyncOperationEntity {

    private String id;
    private String tenant;
    private JobStatus status;
    private final Map<String,Object> data;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public AsyncOperationEntity() {
        this.data = new HashMap<>();
    }
}

package it.doqui.libra.librabl.application.model.jobs.responses;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Status")
public enum JobStatus {
    SUBMITTED,
    PENDING,
    RUNNING,
    WAITING,
    COMPLETED,
    SUCCESS,
    FAILED,
    CANCELLED;

    public boolean isDone() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == SUCCESS;
    }
}

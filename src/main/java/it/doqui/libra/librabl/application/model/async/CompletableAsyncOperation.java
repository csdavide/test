package it.doqui.libra.librabl.application.model.async;

import it.doqui.libra.librabl.application.mappers.AsyncOperationConverter;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
public final class CompletableAsyncOperation<T> implements AsyncOperation<T> {

    private T result;
    private JobStatus status;
    private String message;
    private final String jobId;

    @Setter
    private ZonedDateTime createdAt;

    @Setter
    private ZonedDateTime updatedAt;

    public CompletableAsyncOperation(String jobId) {
        this.jobId = jobId;
        this.status = JobStatus.SUBMITTED;
    }

    public CompletableAsyncOperation(AsyncOperation<?> operation) {
        this.jobId = operation.getJobId();
        this.status = AsyncOperationConverter.status(operation.getStatus());
        this.message = operation.getMessage();
        this.createdAt = operation.getCreatedAt();
        this.updatedAt = operation.getUpdatedAt();
    }

    public void complete(T result) {
        this.result = result;
        this.status = JobStatus.SUCCESS;
    }

    public void setStatus(JobStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}

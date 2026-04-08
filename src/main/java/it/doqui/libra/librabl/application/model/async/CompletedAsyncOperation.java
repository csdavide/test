package it.doqui.libra.librabl.application.model.async;

import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import lombok.Getter;

@Getter
public final class CompletedAsyncOperation<T> implements AsyncOperation<T> {

    private final T result;
    private final JobStatus status;

    public CompletedAsyncOperation(T result, JobStatus status) {
        this.result = result;
        this.status = status;
    }

    public CompletedAsyncOperation(T result) {
        this(result, JobStatus.SUCCESS);
    }

    @Override
    public String getJobId() {
        return null;
    }

    @Override
    public boolean isDone() {
        return true;
    }
}

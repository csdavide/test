package it.doqui.libra.librabl.business.provider.async;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

public final class CompletableAsyncOperation<T> implements AsyncOperation<T> {

    private T result;
    private Status status;

    @Getter
    private String message;
    private final String operationId;

    @Getter
    @Setter
    private ZonedDateTime createdAt;

    @Getter
    @Setter
    private ZonedDateTime updatedAt;

    public CompletableAsyncOperation(String operationId) {
        this.operationId = operationId;
        this.status = Status.SUBMITTED;
    }

    void complete(T result) {
        checkIfCompleted();
        this.result = result;
        this.status = Status.SUCCESS;
    }

    void fail() {
        checkIfCompleted();
        this.status = Status.FAILED;
    }

    void setStatus(Status status, String message) {
        checkIfCompleted();
        this.status = status;
        this.message = message;
    }

    private void checkIfCompleted() {
        if (isDone()) {
            throw new IllegalStateException("Operation already completed");
        }
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public String getOperationId() {
        return operationId;
    }

    @Override
    public boolean isDone() {
        return status == Status.SUCCESS || status == Status.FAILED;
    }

    @Override
    @JsonIgnore
    public T get() {
        return result;
    }
}

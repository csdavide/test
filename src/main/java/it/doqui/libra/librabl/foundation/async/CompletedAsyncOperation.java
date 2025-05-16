package it.doqui.libra.librabl.foundation.async;

public final class CompletedAsyncOperation<T> implements AsyncOperation<T> {

    private final Status status;
    private final T result;

    public CompletedAsyncOperation(Status status, T result) {
        this.status = status;
        this.result = result;
    }

    public CompletedAsyncOperation(T result) {
        this(Status.SUCCESS, result);
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public String getOperationId() {
        return null;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public T get() {
        return result;
    }
}

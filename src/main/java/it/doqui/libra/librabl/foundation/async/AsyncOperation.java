package it.doqui.libra.librabl.foundation.async;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;

public interface AsyncOperation<T> {

    enum Status {
        SUBMITTED,
        RUNNING,
        SUCCESS,
        FAILED
    }

    /**
     * @return status of the operation
     */
    Status getStatus();

    /**
     * @return feedback message
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    default String getMessage() {
        return null;
    }

    /**
     * @return the operationId if the operation was async
     */
    String getOperationId();

    /**
     * Returns {@code true} if this task completed.
     *
     * Completion may be due to normal termination, an exception, or
     * cancellation -- in all of these cases, this method will return
     * {@code true}.
     *
     * @return {@code true} if this task completed
     */
    boolean isDone();

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    default boolean isCompleted() {
        return getStatus() == Status.SUCCESS;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    default boolean isFailed() {
        return getStatus() == Status.FAILED;
    }

    /**
     * Retrieves its result if completed or null otherwise
     *
     * @return the computed result
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    T get();

    /**
     * @return feedback message
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    default ZonedDateTime getCreatedAt() {
        return null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    default ZonedDateTime getUpdatedAt() {
        return null;
    }
}

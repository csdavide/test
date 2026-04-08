package it.doqui.libra.librabl.foundation.async;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;

import static it.doqui.libra.librabl.application.model.jobs.responses.JobStatus.*;

public interface AsyncOperation<T> {

    /**
     * @return status of the operation
     */
    JobStatus getStatus();

    /**
     * @return feedback message
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    default String getMessage() {
        return null;
    }

    /**
     * @return the job id if the operation was async
     */
    @Schema(description = "Job Identifier")
    String getJobId();

    /**
     * @return the operationId if the operation was async
     */
    @Schema(
            deprecated = true,
            description = "Deprecated: use jobId instead"
    )
    @Deprecated
    default String getOperationId() {
        return getJobId();
    }

    /**
     * Returns {@code true} if this task completed.
     *
     * Completion may be due to normal termination, an exception, or
     * cancellation -- in all of these cases, this method will return
     * {@code true}.
     *
     * @return {@code true} if this task completed
     */
    default boolean isDone() {
        return getStatus().isDone();
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    default boolean isCompleted() {
        return getStatus() == COMPLETED || getStatus() == SUCCESS;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    default boolean isFailed() {
        return getStatus() == FAILED || getStatus() == CANCELLED;
    }

    /**
     * Retrieves its result if completed or null otherwise
     *
     * @return the computed result
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    T getResult();

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

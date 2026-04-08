package it.doqui.libra.librabl.application.mappers;

import it.doqui.libra.librabl.foundation.async.FeedbackAsyncOperation;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;

import java.util.HashMap;

public final class AsyncOperationConverter {

    private AsyncOperationConverter() {
        throw new IllegalStateException("Utility class");
    }

    public static FeedbackAsyncOperation map(JobResponse jobResponse) {
        var op = new FeedbackAsyncOperation();
        op.setStatus(status(jobResponse.getStatus()));
        op.setJobId(jobResponse.getJobId());
        op.setCreatedAt(jobResponse.getCreatedAt());
        op.setUpdatedAt(jobResponse.getUpdatedAt());

        var data = new HashMap<String, Object>();
        if (jobResponse.getMessage() != null) {
            data.put("message", jobResponse.getMessage());
        }
        op.setData(data);
        return op;
    }

    public static JobStatus status(JobStatus status) {
        return switch (status) {
            case PENDING -> JobStatus.SUBMITTED;
            case COMPLETED -> JobStatus.SUCCESS;
            case WAITING -> JobStatus.RUNNING;
            case CANCELLED -> JobStatus.FAILED;
            default -> status;
        };
    }

}

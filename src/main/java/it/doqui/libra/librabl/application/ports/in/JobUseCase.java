package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;

import java.util.function.Function;

public interface JobUseCase {
    JobResponse getJob(String jobId, String tenant);
    JobResponse getJob(String jobId);
    JobResponse setJob(String jobId, JobStatus status, JobResult result);
    JobResponse failJob(String jobId, String message);
    JobResponse cancelJob(String jobId, String tenant);
    JobResponse executeJob(JobRequest request);
    void executeAsyncJob(String jobId, String schema);
    void resumeAsyncJob(String jobId, String schema, JobStatus status, Function<JobResult,JobResult> updater);
}

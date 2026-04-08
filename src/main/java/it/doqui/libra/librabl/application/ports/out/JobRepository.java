package it.doqui.libra.librabl.application.ports.out;

import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResponse;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface JobRepository {
    JobResponse createJob(JobRequest request, String queue);
    JobResponse getJob(String jobId);
    JobRequest takeJob(String jobId, String schema, BiConsumer<String, Set<String>> onAuth);
    JobResponse setJob(String jobId, JobStatus status, JobResult result);
    JobResponse updateJob(String jobId, Function<JobResult,JobResult> updater);
    JobResponse updateJob(String jobId, JobStatus status, Function<JobResult,JobResult> updater);
    JobResponse failJob(String jobId, String message);
    JobResponse cancelJob(String jobId);
    void deleteJob(String jobId, Predicate<JobResponse> filter);
}

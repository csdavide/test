package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;

import java.util.Set;

public interface JobExecutor {
    String getHandledKind();
    JobResult executeJob(JobRequest request);

    default boolean isLongOperationRequired() {
        return false;
    }

    default boolean isMasterSchemaRequired() {
        return false;
    }

    default Set<String> requiredRoles() {
        return Set.of();
    }
}

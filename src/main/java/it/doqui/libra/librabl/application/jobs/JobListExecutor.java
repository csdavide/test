package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.application.model.jobs.requests.JobListRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobListResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@ApplicationScoped
@Slf4j
public class JobListExecutor implements JobExecutor {

    @Inject
    JobUseCase jobService;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public String getHandledKind() {
        return "jobs";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof JobListRequest jobListRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        if (jobListRequest.isAtomic()) {
            return transactionManagerPort.requireNew(() -> {
                transactionManagerPort.options().disableWithInTxMode();
                return transactionManagerPort.performNew(tx -> {
                    var result = executeJobList(jobListRequest.getJobs(), true);
                    return PerformResult.<JobResult>builder().mode(PerformResult.Mode.SYNC).result(result).build();
                });
            });
        } else {
            return executeJobList(jobListRequest.getJobs(), false);
        }
    }

    private JobResult executeJobList(List<JobRequest> jobs, boolean stopOnFailure) {
        var result = new JobListResult();
        int count = 0;
        for (var job : jobs) {
            job.setMode(OperationMode.SYNC);
            var response = jobService.executeJob(job);
            result.getJobs().add(response);
            if (response.isCompleted()) {
                count++;
            } else if (stopOnFailure) {
                result.setAborted(true);
                break;
            }
        }
        result.setCompleteness(jobs.isEmpty() ? 1.0 : (double) count / (double) jobs.size());
        return result;
    }

}

package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.model.jobs.requests.ImportJobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.ImportResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.ports.in.ImportUseCase;
import it.doqui.libra.librabl.application.ports.out.JobRepository;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@ApplicationScoped
@Slf4j
public class ImportJobExecutor implements JobExecutor {

    @Inject
    ImportUseCase importUseCase;

    @Inject
    JobRepository jobRepository;

    @Inject
    SessionContext sessionContext;

    @Override
    public String getHandledKind() {
        return "import";
    }

    @Override
    public boolean isLongOperationRequired() {
        return true;
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof ImportJobRequest importJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var imp = importJobRequest.getImportStatement();
        if (imp == null) {
            throw new BadRequestException("No import statement specified in " + request.getKind() + " job");
        }

        final var jobId = sessionContext.getJobId();
        if (jobId != null) {
            jobRepository.updateJob(jobId, (result) -> {
                var r = new ImportResult();
                r.setKind(getHandledKind());
                r.setAffectedNodes(0);
                return r;
            });
        }

        Consumer<Long> countConsumer = (count) -> {
            if (jobId != null && count != null) {
                jobRepository.updateJob(jobId, (result) -> {
                    if (result instanceof ImportResult r) {
                        r.setAffectedNodes(count);
                    }

                    return null;
                });
            }
        };

        var result = importUseCase.importDataSet(importJobRequest.getSource(), imp, countConsumer);
        result.setKind(getHandledKind());
        return result;
    }
}

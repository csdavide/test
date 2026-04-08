package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.ports.in.MultipleNodeOperationUseCase;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.UpdateJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class UpdateJobExecutor implements JobExecutor {

    @Inject
    MultipleNodeOperationUseCase multipleNodeOperationService;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public String getHandledKind() {
        return "update";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof UpdateJobRequest updateJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        int count = transactionManagerPort.perform(() -> {
            var queryParameters = updateJobRequest.getQuery();
            return multipleNodeOperationService.updateNodes(queryParameters, updateJobRequest.getUpdate(), updateJobRequest.getOptions());
        });

        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(count);
        return result;
    }
}

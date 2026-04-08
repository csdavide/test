package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.ports.in.MultipleNodeOperationUseCase;
import it.doqui.libra.librabl.application.model.jobs.requests.DeleteJobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@ApplicationScoped
@Slf4j
public class DeleteJobExecutor implements JobExecutor {

    @Inject
    MultipleNodeOperationUseCase multipleNodeOperationService;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public String getHandledKind() {
        return "delete";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof DeleteJobRequest deleteJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var deleteMode = Optional.ofNullable(deleteJobRequest.getDeleteMode()).orElse(DeleteMode.DELETE);
        int count = transactionManagerPort.perform(() -> multipleNodeOperationService.deleteNodes(deleteJobRequest.getQuery(), deleteMode));
        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(count);
        return result;
    }

}

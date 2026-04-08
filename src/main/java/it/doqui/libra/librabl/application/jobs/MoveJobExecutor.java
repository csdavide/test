package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.MoveJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class MoveJobExecutor implements JobExecutor {

    @Inject
    NodeUseCase nodeService;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public String getHandledKind() {
        return "move";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof MoveJobRequest moveJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        if (moveJobRequest.getNode() == null) {
            throw new BadRequestException("No source node specified in " + request.getKind() + " job");
        }

        if (moveJobRequest.getDestination() == null) {
            throw new BadRequestException("No destination specified in " + request.getKind() + " job");
        }

        long affectedNodes = transactionManagerPort
                .requireNew(() -> nodeService.moveNode(moveJobRequest.getNode(), moveJobRequest.getDestination()));

        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(affectedNodes);
        return result;
    }
}

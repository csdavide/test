package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.ports.in.AssociationUseCase;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.UnlinkJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class UnlinkJobExecutor implements JobExecutor {

    @Inject
    AssociationUseCase associationService;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public String getHandledKind() {
        return "unlink";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof UnlinkJobRequest unlinkJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var link = unlinkJobRequest.getUnlink();
        if (link == null) {
            throw new BadRequestException("No link parameters specified in " + request.getKind() + " job");
        }

        var vertex = unlinkJobRequest.getNode();
        long affectedNodes = transactionManagerPort.performNew(tx -> {
            long count = associationService.unlinkNode(vertex, link);
            return PerformResult.<Long>builder().result(count).count(count).build();
        });

        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(affectedNodes);
        return result;
    }
}

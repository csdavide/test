package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.ports.in.AssociationUseCase;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.LinkJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class LinkJobExecutor implements JobExecutor {

    @Inject
    AssociationUseCase associationService;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public String getHandledKind() {
        return "link";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof LinkJobRequest linkJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var link = linkJobRequest.getLink();
        if (link == null) {
            throw new BadRequestException("No link parameters specified in " + request.getKind() + " job");
        }

        var vertex = linkJobRequest.getNode();
        long affectedNodes = transactionManagerPort.performNew(tx -> {
            var r = associationService.linkNode(vertex, link, false);
            return PerformResult.<Long>builder().result(r.affectedNodes()).build();
        });

        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(affectedNodes);
        return result;
    }
}

package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.TxDAO;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.ports.in.ArchiveUseCase;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.association.LinkMode;
import it.doqui.libra.librabl.domain.model.graph.BindingType;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.RestoreJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@ApplicationScoped
@Slf4j
public class RestoreJobExecutor implements JobExecutor {

    @Inject
    ArchiveUseCase archiveService;

    @Inject
    TxDAO txDAO;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    public String getHandledKind() {
        return "restore";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof RestoreJobRequest restoreJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var restore = restoreJobRequest.getRestore();
        if (restore == null) {
            throw new BadRequestException("No restore parameters specified in " + request.getKind() + " job");
        }

        if (restore.getDestination() != null) {
            if (restore.getDestination().getBindingType() != null && restore.getDestination().getBindingType() != BindingType.HARD) {
                throw new BadRequestException("Restore requires an hard binding");
            }
        }

        long affectedNodes = transactionManagerPort.performNew(tx -> {
            var restoreMode = Optional.ofNullable(restore.getLinkMode()).orElse(LinkMode.ALL);
            archiveService.restoreNode(restore.getNode(), restore.getDestination(), restoreMode);
            long count = txDAO.count(tx, Long.MAX_VALUE);
            return PerformResult.<Long>builder().result(count).build();
        });

        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(affectedNodes);
        return result;
    }
}

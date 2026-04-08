package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.RenameJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.statements.RenameStatement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
@Slf4j
public class RenameJobExecutor implements JobExecutor {

    @Inject
    NodeUseCase nodeService;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public String getHandledKind() {
        return "rename";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof RenameJobRequest renameJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var rename = renameJobRequest.getRename();
        if (StringUtils.isBlank(rename.getName())) {
            throw new BadRequestException("No name specified in " + request.getKind() + " job");
        }

        if (rename.getRenameMode() == RenameStatement.RenameMode.SPECIFIC_PARENT && rename.getParent() == null) {
            throw new BadRequestException("Parent association is mandatory for rename in specific parent mode in " + request.getKind() + " job");
        }

        long affectedNodes = transactionManagerPort
                .requireNew(() -> nodeService.renameNode(renameJobRequest.getNode(), renameJobRequest.getRename()));

        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(affectedNodes);
        return result;
    }
}

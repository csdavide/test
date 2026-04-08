package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.out.ReindexPort;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.ReindexJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

@ApplicationScoped
@Slf4j
public class ReindexJobExecutor implements JobExecutor {

    @Inject
    ReindexPort reindexService;

    @Inject
    AuthenticationManagerPort authenticationManagerPort;

    @Override
    public String getHandledKind() {
        return "reindex";
    }

    @Override
    public boolean isLongOperationRequired() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of(UserContext.ROLE_SYSADMIN, UserContext.ROLE_POWERADMIN);
    }

    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof ReindexJobRequest reindexJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        if (StringUtils.isNotBlank(reindexJobRequest.getTenant())) {
            authenticationManagerPort.autenticateIfRequired(TenantRef.valueOf(reindexJobRequest.getTenant()), true);
        }

        boolean recursive = false;
        int blockSize = 40;
        if (reindexJobRequest.getReindex() != null) {
            recursive = reindexJobRequest.getReindex().isRecursive();
            if (reindexJobRequest.getReindex().getBlockSize() > 0) {
                blockSize = reindexJobRequest.getReindex().getBlockSize();
            }
        }

        return reindexService.reindexSubTree(reindexJobRequest.getNode(), blockSize, recursive);
    }
}

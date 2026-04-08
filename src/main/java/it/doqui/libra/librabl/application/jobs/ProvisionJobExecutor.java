package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.ports.in.TenantUseCase;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.ProvisionJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.ProvisionResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@ApplicationScoped
@Slf4j
public class ProvisionJobExecutor implements JobExecutor {

    @Inject
    TenantUseCase tenantUseCase;

    @Override
    public String getHandledKind() {
        return "provision";
    }

    @Override
    public boolean isLongOperationRequired() {
        return true;
    }

    @Override
    public boolean isMasterSchemaRequired() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of(UserContext.ROLE_SYSADMIN);
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof ProvisionJobRequest provisionJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var createdTenant = tenantUseCase.createTenant(provisionJobRequest.getProvision());

        var result = new ProvisionResult();
        result.setKind(request.getKind());
        result.setProvisionedTenant(createdTenant);
        return result;
    }

}

package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.SchemaUpgradeJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class SchemaUpgradeJobExecutor implements JobExecutor {

    @Inject
    TenantRepository tenantRepository;

    @Override
    public String getHandledKind() {
        return "schema-upgrade";
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
        if (!(request instanceof SchemaUpgradeJobRequest schemaUpgradeJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        tenantRepository.upgradeSchema(
                Optional.ofNullable(schemaUpgradeJobRequest.getSchema()).orElse(Set.of()),
                schemaUpgradeJobRequest.getFromVersion(),
                schemaUpgradeJobRequest.getTargetVersion());

        return null;
    }
}

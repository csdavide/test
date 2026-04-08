package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.VolumeDAO;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.VolumeCalculationJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CalculatedVolumesResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@ApplicationScoped
@Slf4j
public class VolumeCalculationJobExecutor implements JobExecutor {

    @Inject
    VolumeDAO volumeDAO;

    @Override
    public String getHandledKind() {
        return "volume";
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
        if (!(request instanceof VolumeCalculationJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var result = new CalculatedVolumesResult();
        result.setKind(request.getKind());
        result.setVolumes(volumeDAO.getVolumes());
        return result;
    }
}

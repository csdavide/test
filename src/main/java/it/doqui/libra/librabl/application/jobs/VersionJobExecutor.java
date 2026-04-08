package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.ports.in.VersioningUseCase;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.VersionJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.VersionResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class VersionJobExecutor implements JobExecutor {

    @Inject
    VersioningUseCase versioningUseCase;

    @Override
    public String getHandledKind() {
        return "version";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof VersionJobRequest versionJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var version = versionJobRequest.getVersion();
        if (version == null) {
            throw new BadRequestException("No move parameters specified in " + request.getKind() + " job");
        }

        var versionItem = versioningUseCase.createNodeVersion(versionJobRequest.getNode(), version.getTag()).orElse(null);
        var result = new VersionResult();
        result.setKind(request.getKind());
        if (versionItem != null) {
            result.setNode(versionItem.getItem());

            var versionInfo = new VersionResult.VersionInfo();
            versionInfo.setUuid(versionItem.getVersionUUID());
            versionInfo.setVersion(versionItem.getVersion());
            versionInfo.setTag(versionItem.getVersionTag());
            versionInfo.setCreatedAt(versionItem.getCreatedAt());
            versionInfo.setCreatedBy(versionItem.getCreatedBy());
            result.setVersion(versionInfo);
        }

        return result;
    }
}

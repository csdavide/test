package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.ports.in.VersioningUseCase;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.ReplaceJobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.CountResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ReplaceJobExecutor implements JobExecutor {

    @Inject
    VersioningUseCase versioningUseCase;

    @Override
    public String getHandledKind() {
        return "replace";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof ReplaceJobRequest replaceJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var source = replaceJobRequest.getReplace();
        if (source == null) {
            throw new BadRequestException("No move parameters specified in " + request.getKind() + " job");
        }

        versioningUseCase.replaceNodeMetadata(replaceJobRequest.getNode(), source.getNode(), source.getVersion());
        var result = new CountResult();
        result.setKind(request.getKind());
        result.setAffectedNodes(1);
        return result;
    }
}

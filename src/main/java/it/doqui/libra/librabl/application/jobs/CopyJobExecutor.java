package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.application.model.jobs.requests.CopyJobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.UUIDResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class CopyJobExecutor implements JobExecutor {

    @Inject
    NodeUseCase nodeService;

    @Override
    public String getHandledKind() {
        return "copy";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof CopyJobRequest copyJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var source = copyJobRequest.getVertex();
        var destination = copyJobRequest.getLink();
        var copy = copyJobRequest.getCopy();
        var uuid = nodeService.copyNode(source, destination, copy.isCopyChildren(), !copy.isExcludeAssociations(), copy.getCopyMode());
        var result = new UUIDResult();
        result.setKind(request.getKind());
        result.setUuid(uuid);
        return result;
    }
}

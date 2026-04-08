package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.application.model.jobs.requests.CreateJobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.model.jobs.responses.UUIDResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class CreateJobExecutor implements JobExecutor {

    @Inject
    NodeUseCase nodeService;

    @Override
    public String getHandledKind() {
        return "create";
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof CreateJobRequest createJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var uuid = nodeService.createOrUpdateNode(createJobRequest.getCreate(), createJobRequest.getOptions());
        var result = new UUIDResult();
        result.setKind(request.getKind());
        result.setUuid(uuid);
        return result;
    }
}

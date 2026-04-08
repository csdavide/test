package it.doqui.libra.librabl.application.jobs;

import it.doqui.libra.librabl.application.model.jobs.requests.ExportJobRequest;
import it.doqui.libra.librabl.application.model.jobs.requests.JobRequest;
import it.doqui.libra.librabl.application.model.jobs.responses.ExportResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobResult;
import it.doqui.libra.librabl.application.ports.in.MultipleNodeOperationUseCase;
import it.doqui.libra.librabl.application.ports.in.NodeUseCase;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.ports.out.FileAggregatorPort;
import it.doqui.libra.librabl.domain.ports.out.FileRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Set;

@ApplicationScoped
@Slf4j
public class ExportJobExecutor implements JobExecutor {

    @Inject
    MultipleNodeOperationUseCase multipleNodeOperationService;

    @Inject
    NodeUseCase nodeService;

    @Inject
    FileAggregatorPort fileAggregatorPort;

    @Inject
    SessionContext sessionContext;

    @Override
    public String getHandledKind() {
        return "export";
    }

    @Override
    public boolean isLongOperationRequired() {
        return true;
    }

    @Override
    public JobResult executeJob(JobRequest request) {
        if (!(request instanceof ExportJobRequest exportJobRequest)) {
            throw new IllegalArgumentException("Invalid job request: " + request);
        }

        var files = new ArrayList<FileRequest>();
        var count = multipleNodeOperationService.findNodes(exportJobRequest.getQuery(), uuids -> {
            var nodes = nodeService.listNodeMetadata(uuids, Set.of(MapOption.DEFAULT), null, null, QueryScope.SEARCH);
            for (var n : nodes) {
                int i = 0;
                for (var c : n.getContents()) {
                    var filename = switch (exportJobRequest.getFileNameFormat()) {
                        case NAME -> c.getFileName();
                        case UUID -> n.getUuid() + ( i > 0 ? String.format("_%02d", i) : "");
                    };

                    var r = FileRequest.builder()
                            .fileURI(c.getFileURI())
                            .filename(filename)
                            .build();
                    files.add(r);

                    i++;
                }
            }

            return nodes.size();
        });

        fileAggregatorPort.submitAggregation(sessionContext.getJobId(), files, exportJobRequest.getDuration());

        var result = new ExportResult();
        result.setKind(request.getKind());
        result.setRequestedFiles(files.size());
        result.setProcessedNodes(count);
        return result;
    }
}

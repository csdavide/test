package it.doqui.libra.librabl.infrastructure.adapters.input.messaging.aggregation;

import io.smallrye.reactive.messaging.amqp.IncomingAmqpMetadata;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.json.JsonObject;
import it.doqui.libra.librabl.application.model.jobs.responses.ExportResult;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import it.doqui.libra.librabl.application.ports.in.JobUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
@Slf4j
public class FileAggregationFeedbackConsumer {

    @ConfigProperty(name = "libra.files.download-base-url", defaultValue = "true")
    Optional<URL> baseURL;

    @Inject
    JobUseCase jobService;

    @Incoming("file-agg-feedback-in")
    @Blocking
    @ActivateRequestContext
    public CompletionStage<Void> receive(Message<JsonObject> message) {
        var metadata = message.getMetadata(IncomingAmqpMetadata.class).orElseThrow(() -> new IllegalStateException("No metadata"));
        var jobId = metadata.getCorrelationId();
        var properties = metadata.getProperties();
        var payload = message.getPayload().mapTo(FileAggregationFeedback.class);
        log.info("Received message for jobId {} (properties {}): {}", jobId, properties, message.getPayload());
        var workspace = properties.getString("x-workspace");
        jobService.resumeAsyncJob(jobId, workspace, JobStatus.COMPLETED, (result) -> {
            if (result instanceof ExportResult exportResult) {
                var downloadURI = baseURL.map(URL::toString)
                        .map(url -> url.endsWith("/") ? url : url + "/")
                        .map(url -> url + payload.getFileAccess().usageToken())
                        .map(URI::create).orElse(payload.getFileAccess().fileUri());

                exportResult.setDownloadUri(downloadURI);
                exportResult.setAggregatedFileCount(payload.getFileCount());
            }

            return null;
        });
        return message.ack();
    }

}

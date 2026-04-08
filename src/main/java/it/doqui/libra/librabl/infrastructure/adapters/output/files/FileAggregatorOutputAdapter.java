package it.doqui.libra.librabl.infrastructure.adapters.output.files;

import io.smallrye.reactive.messaging.amqp.OutgoingAmqpMetadata;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.FileAggregatorPort;
import it.doqui.libra.librabl.domain.ports.out.FileRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Duration;
import java.util.Collection;

@ApplicationScoped
@Slf4j
public class FileAggregatorOutputAdapter implements FileAggregatorPort {

    @Inject
    @Channel("file-aggregation-out")
    Emitter<FileAggregationMessage> emitter;

    @Inject
    SessionContext sessionContext;

    @Override
    public void submitAggregation(String jobId, Collection<FileRequest> requests, Duration duration) {
        var payload = new FileAggregationMessage(requests, duration);
        var metadata = OutgoingAmqpMetadata.builder()
                .withContentType("application/json")
                .withCorrelationId(jobId)
                .withApplicationProperty("x-user-id", sessionContext.getUserContext().getAuthority())
                .withApplicationProperty("x-tenant",  sessionContext.getTenant())
                .withApplicationProperty("x-workspace", sessionContext.getUserContext().getDbSchema())
                .withDurable(true)
                .build();
        emitter.send(Message.of(payload).addMetadata(metadata));
    }
}

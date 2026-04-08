package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.AsyncOperationService;
import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.NodeOperation;
import it.doqui.libra.librabl.infrastructure.adapters.input.operations.OperationParameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@ApplicationScoped
@Unremovable
@Slf4j
public class AsyncOperationMessageHandler implements MessageHandler {

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void handleMessage(Message message) throws JMSException {
        var taskId = message.getStringProperty("taskId");
        try {
            log.info("Processing async node task {}", taskId);
            var messageOperations = message.getStringProperty("operations");
            if (messageOperations == null) {
                throw new IllegalArgumentException("No operation specified");
            }

            final Collection<NodeOperation> operations = objectMapper.readValue(messageOperations, new TypeReference<>() {});
            performOperations(taskId, operations, OperationParameters.builder().mode(OperationMode.SYNC).build());
        } catch (JMSException | JsonProcessingException e) {
            log.error("Async operation {} failed: {}", taskId, e.getMessage(), e);
        }
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.UPDATE)
    void performOperations(String taskId, Collection<NodeOperation> operations, OperationParameters params) {
        asyncOperationService.executeAsyncOperations(taskId, operations, params);
    }

    @Override
    public boolean requireTenant() {
        return true;
    }
}

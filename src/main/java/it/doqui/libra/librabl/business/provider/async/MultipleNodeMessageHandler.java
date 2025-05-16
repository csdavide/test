package it.doqui.libra.librabl.business.provider.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.interfaces.MultipleNodeOperationService;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.node.NodeOperation;
import lombok.extern.slf4j.Slf4j;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.util.Collection;
import java.util.Map;

@ApplicationScoped
@Unremovable
@Slf4j
public class MultipleNodeMessageHandler implements MessageHandler {

    @Inject
    MultipleNodeOperationService multipleNodeOperationService;

    @Inject
    AsyncOperationService asyncOperationService;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void handleMessage(Message message) throws JMSException {
        var taskId = message.getStringProperty("taskId");
        log.info("Processing async node task {}", taskId);
        final Collection<NodeOperation> operations;
        try {
            operations = objectMapper.readValue(message.getStringProperty("operations"), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new SystemException(e);
        }

        try {
            multipleNodeOperationService.performOperations(operations, null, OperationMode.SYNC);
            asyncOperationService.completeTask(taskId, AsyncOperation.Status.SUCCESS, null);
        } catch (RuntimeException e) {
            log.error(String.format("Async operation %s failed: %s", taskId, e.getMessage()), e);
            asyncOperationService.completeTask(taskId, AsyncOperation.Status.FAILED, Map.of("message", e.getMessage()));
        }
    }

    @Override
    public boolean requireTenant() {
        return true;
    }
}

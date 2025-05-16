package it.doqui.libra.librabl.business.provider.async;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.engine.AssociationManager;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.views.OperationMode;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import lombok.extern.slf4j.Slf4j;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.util.Map;

@ApplicationScoped
@Unremovable
@Slf4j
public class LinkMessageHandler implements MessageHandler {

    @Inject
    AssociationManager associationManager;

    @Inject
    AsyncOperationService asyncOperationService;

    @Override
    public void handleMessage(Message message) throws JMSException {
        var taskId = message.getStringProperty("taskId");
        log.info("Processing async link task {}", taskId);
        var uuid = message.getStringProperty("child");
        var linkItem = new LinkItemRequest();
        linkItem.setVertexUUID(message.getStringProperty("parent"));
        linkItem.setRelationship(RelationshipKind.valueOf(message.getStringProperty("relationship")));
        linkItem.setTypeName(message.getStringProperty("type"));
        linkItem.setName(message.getStringProperty("name"));
        linkItem.setHard(message.getBooleanProperty("hard"));
        try {
            var result = associationManager.linkNode(uuid, linkItem, OperationMode.SYNC);
            log.info("Async operation {} successfully completed: got '{}'", taskId, result.get());
            asyncOperationService.completeTask(taskId, AsyncOperation.Status.SUCCESS, Map.of("result", result.isDone() ? result.get() : result));
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

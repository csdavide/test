package it.doqui.libra.librabl.business.provider.integration.messaging.consumers;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.async.LinkMessageHandler;
import it.doqui.libra.librabl.business.provider.async.MultipleNodeMessageHandler;
import it.doqui.libra.librabl.business.provider.integration.indexing.impl.IndexingMessageHandler;
import it.doqui.libra.librabl.business.provider.integration.indexing.impl.ReindexRangeMessageHandler;
import it.doqui.libra.librabl.business.provider.integration.indexing.impl.SolrSyncMessageHandler;
import it.doqui.libra.librabl.business.provider.integration.messaging.events.EventMessageHandler;
import it.doqui.libra.librabl.business.provider.management.RemovedNodeCleanerJob;
import it.doqui.libra.librabl.business.provider.management.TransactionCleanerJob;
import it.doqui.libra.librabl.business.provider.management.VolumeCalculatorJob;
import it.doqui.libra.librabl.business.service.async.AsyncOperationService;
import it.doqui.libra.librabl.business.service.auth.AuthenticationService;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import static it.doqui.libra.librabl.business.provider.integration.messaging.MessageType.*;

@ApplicationScoped
@Slf4j
@Unremovable
public class MessageDispatcher {

    @Inject
    AuthenticationService authenticationService;

    @Inject
    IndexingMessageHandler indexingMessageHandler;

    @Inject
    ReindexRangeMessageHandler reindexRangeMessageHandler;

    @Inject
    LinkMessageHandler linkMessageHandler;

    @Inject
    MultipleNodeMessageHandler multipleNodeMessageHandler;

    @Inject
    EventMessageHandler eventMessageHandler;

    @Inject
    SolrSyncMessageHandler solrSyncMessageHandler;

    @Inject
    AsyncOperationService asyncOperationService;

    @ActivateRequestContext
    public void process(Message message) throws JMSException {
        log.debug("Processing message {}", message.getJMSMessageID());
        final var type = StringUtils.stripToEmpty(message.getJMSType());
        final var handler = switch (type) {
            case REINDEX -> indexingMessageHandler;
            case REINDEX_RANGE -> reindexRangeMessageHandler;
            case LINK -> linkMessageHandler;
            case MULTINODE -> multipleNodeMessageHandler;
            case DISTRIBUTED_EVENT -> eventMessageHandler;
            case SOLR_SYNC -> solrSyncMessageHandler;
            case TX_CLEAN -> Arc.container().select(TransactionCleanerJob.class).get();
            case NODES_CLEAN -> Arc.container().select(RemovedNodeCleanerJob.class).get();
            case CALC_VOLUMES -> Arc.container().select(VolumeCalculatorJob.class).get();
            default -> {
                log.warn("Unknown message type {}", type);
                throw new BadMessageException();
            }
        };

        String taskId = null;
        var registered = false;
        try {
            if (handler.requireTracing(message)) {
                taskId = message.getStringProperty("taskId");
                if (StringUtils.isNotBlank(taskId)) {
                    registered = true;
                }
            }

            handleMessage(handler, message);
        } catch (RuntimeException | JMSException e) {
            if (registered) {
                log.error(String.format("Async operation %s failed: %s", taskId, e.getMessage()), e);
                asyncOperationService.completeTask(taskId, AsyncOperation.Status.FAILED, Map.of("message", e.getMessage()));
            } else {
                throw e;
            }
        }
    }

    private void handleMessage(MessageHandler handler, Message message) throws JMSException {
        if (handler.requireTenant()) {
            var authority = message.getStringProperty("authority");
            if (authority != null) {
                authenticationService.authenticateUser(AuthorityRef.valueOf(authority), null, UserContext.Mode.ASYNC);
            } else {
                var tenant = message.getStringProperty("tenant");
                if (tenant != null) {
                    authenticationService.authenticateUser(new AuthorityRef("admin", TenantRef.valueOf(tenant)), null, UserContext.Mode.ASYNC);
                } else {
                    log.warn("Missing both authority and tenant in message {}: type {}", message.getJMSMessageID(), message.getJMSType());
                    throw new BadMessageException();
                }
            }
        }

        handler.handleMessage(message);
    }
}

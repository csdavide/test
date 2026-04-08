package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.common.RemovedNodeCleanerJob;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static it.doqui.libra.librabl.application.model.messaging.MessageType.*;

@IfBuildProperty(name = "libra.module.async.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
@Unremovable
public class MessageDispatcher {

    @Inject
    AuthenticationManagerPort authenticationManagerPort;

    @Inject
    IndexingMessageHandler indexingMessageHandler;

    @Inject
    EventMessageHandler eventMessageHandler;

    @Inject
    AsyncOperationMessageHandler operationMessageHandler;

    @Inject
    JobMessageHandler jobMessageHandler;

    @Inject
    SessionContext sessionContext;

    @ActivateRequestContext
    public void process(Message message) throws JMSException {
        final var type = StringUtils.stripToEmpty(message.getJMSType());
        log.debug("Processing message {} of type {}", message.getJMSMessageID(), type);
        final var handler = switch (type) {
            case JOB -> jobMessageHandler;
            case REINDEX -> indexingMessageHandler;
            case MULTINODE, OPERATIONS, OPERATION -> operationMessageHandler;
            case DISTRIBUTED_EVENT -> eventMessageHandler;
            case NODES_CLEAN -> Arc.container().select(RemovedNodeCleanerJob.class).get();
            default -> {
                log.warn("Unknown message type {}", type);
                throw new BadMessageException();
            }
        };

        handleMessage(handler, message);
    }

    private void handleMessage(MessageHandler handler, Message message) throws JMSException {
        if (handler.requireTenant()) {
            var schema = message.getStringProperty("workspace");
            if (schema == null) {
                var token = message.getStringProperty("token");
                if (token != null) {
                    long t0 = message.propertyExists("timestamp") ? message.getLongProperty("timestamp") : message.getJMSTimestamp();
                    var time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t0), ZoneId.systemDefault());
                    authenticationManagerPort.authenticateWithToken(token, null, time, SessionMode.ASYNC);
                } else {
                    final AuthorityRef authorityRef;
                    var authority = message.getStringProperty("authority");
                    if (authority != null) {
                        authorityRef = AuthorityRef.valueOf(authority);
                    } else {
                        var tenant = message.getStringProperty("tenant");
                        if (tenant != null) {
                            authorityRef = new AuthorityRef("admin", TenantRef.valueOf(tenant));
                        } else {
                            log.warn("Missing both authority and tenant in message {}: type {}", message.getJMSMessageID(), message.getJMSType());
                            throw new BadMessageException();
                        }
                    }

                    var roles = message.getStringProperty("roles");
                    final Set<String> additionalRoles = roles != null ? new HashSet<>(Arrays.asList(roles.split(","))) : null;
                    authenticationManagerPort.authenticateUser(authorityRef, null, additionalRoles, SessionMode.ASYNC);
                }
            } // end if schema absent

            sessionContext.setApiLevel(2);
            sessionContext.setChannel(UserContext.CHANNEL_JMS);

            var taskId = message.getStringProperty("taskId");
            if (StringUtils.isNotBlank(taskId)) {
                sessionContext.setOperationId(taskId);
            }
        }

        handler.handleMessage(message);
    }
}

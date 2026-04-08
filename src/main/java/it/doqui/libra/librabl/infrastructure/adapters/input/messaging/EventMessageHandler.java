package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.application.model.events.EventType;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.infrastructure.platform.events.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.*;

@ApplicationScoped
@Unremovable
@Slf4j
public class EventMessageHandler implements MessageHandler {

    @Inject
    @Any
    Event<DistributedEvent> event;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Override
    public void handleMessage(Message message) throws JMSException {
        var type = message.getStringProperty("event");
        var sender = message.getStringProperty("sender");
        var myself = transactionManagerPort.getInstanceId();
        if (StringUtils.isBlank(type)) {
            log.warn("Ignoring invalid event {}", message);
            return;
        }
        if (Strings.CS.equals(sender, myself) && !BooleanUtils.toBoolean(message.getStringProperty("includeMySelf"))) {
            log.debug("Ignoring event {} sent by me", type);
            return;
        }

        final Collection<DistributedEvent> notifications = switch (type) {
            case EventType.RELOAD_TENANT -> List.of(new SchemaEvent());
            case EventType.RELOAD_MIMETYPES -> List.of(new MimeTypeReloadEvent());
            case EventType.CLEAN_CACHE -> List.of(new CleanCacheEvent(), new MimeTypeReloadEvent());
            case EventType.SYSTEM_CHECK -> List.of(new SystemCheckEvent());
            case EventType.JOB_ABORT -> List.of(new JobAbortEvent());
            default -> List.of(new DistributedEvent());
        };

        for (var notification : notifications) {
            notification.setType(type);
            notification.setSender(sender);
            notification.setId(Optional.ofNullable(message.getStringProperty("id")).orElse(message.getJMSMessageID()));
            notification.setCorrelationId(Optional.ofNullable(message.getStringProperty("correlationId")).orElse(message.getJMSCorrelationID()));

            var tenant = message.getStringProperty("tenant");
            if (StringUtils.isNotBlank(tenant)) {
                notification.setTenantRef(TenantRef.valueOf(tenant));
            }

            if (message instanceof MapMessage mapMessage) {
                var customKeys = Set.of("tenant", "sender", "event", "id", "correlationId");
                var map = new HashMap<String, Object>();
                var enumeration = mapMessage.getMapNames();
                while (enumeration.hasMoreElements()) {
                    var name = enumeration.nextElement().toString();
                    if (!customKeys.contains(name)) {
                        var value = mapMessage.getObject(name);
                        map.put(name, value);
                    }
                }
                notification.setData(map);
            }



            event.fire(notification);
        }
    }

}

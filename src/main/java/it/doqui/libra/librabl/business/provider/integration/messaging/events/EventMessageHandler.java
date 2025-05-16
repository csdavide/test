package it.doqui.libra.librabl.business.provider.integration.messaging.events;

import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.foundation.TenantRef;
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

import java.util.HashMap;
import java.util.Optional;

@ApplicationScoped
@Unremovable
@Slf4j
public class EventMessageHandler implements MessageHandler {

    @Inject
    @Any
    Event<DistributedEvent> event;

    @Override
    public void handleMessage(Message message) throws JMSException {
        var type = message.getStringProperty("event");
        var sender = message.getStringProperty("sender");
        var myself = TransactionService.current().getInstanceId();
        if (StringUtils.equals(sender, myself) && !BooleanUtils.toBoolean(message.getStringProperty("includeMySelf"))) {
            log.debug("Ignoring event {} sent by me", type);
            return;
        }

        final DistributedEvent notification = switch (type) {
            case EventType.RELOAD_TENANT -> new SchemaEvent();
            case EventType.CLEAN_CACHE -> new CleanCacheEvent();
            case EventType.SYSTEM_CHECK -> new SystemCheckEvent();
            default -> new DistributedEvent();
        };
        notification.setType(type);
        notification.setSender(sender);
        notification.setId(Optional.ofNullable(message.getStringProperty("id")).orElse(message.getJMSMessageID()));
        notification.setCorrelationId(Optional.ofNullable(message.getStringProperty("correlationId")).orElse(message.getJMSCorrelationID()));

        if (message instanceof MapMessage mapMessage) {
            var map = new HashMap<String, Object>();
            var enumeration = mapMessage.getMapNames();
            while (enumeration.hasMoreElements()) {
                var name = enumeration.nextElement().toString();
                var value = mapMessage.getObject(name);
                map.put(name, value);
            }
            notification.setData(map);
        }

        var tenant = message.getStringProperty("tenant");
        if (StringUtils.isNotBlank(tenant)) {
            notification.setTenantRef(TenantRef.valueOf(tenant));
        }

        event.fire(notification);
    }

    @Override
    public boolean requireTenant() {
        return false;
    }
}

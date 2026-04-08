package it.doqui.libra.librabl.infrastructure.platform.events;

import it.doqui.libra.librabl.application.model.configuration.AsyncConfig;
import it.doqui.libra.librabl.application.model.events.SendEventRequest;
import it.doqui.libra.librabl.application.ports.out.EventRepository;
import it.doqui.libra.librabl.application.model.messaging.MessageType;
import it.doqui.libra.librabl.application.ports.out.MessageSenderPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;

@ApplicationScoped
public class EventSender implements EventRepository {

    @Inject
    MessageSenderPort producer;

    @Inject
    AsyncConfig asyncConfig;

    @Override
    public void sendEvent(SendEventRequest request) {
        var map = new HashMap<>(request.getProperties());
        map.put("event", request.getEvent());
        map.put("tenant", request.getTenant());
        producer.submit(MessageType.DISTRIBUTED_EVENT, map, 0, 0, "topic:" + asyncConfig.producer().eventsTopic());
    }

}

package it.doqui.libra.librabl.application.ports.out;

import it.doqui.libra.librabl.application.model.events.SendEventRequest;

public interface EventRepository {
    void sendEvent(SendEventRequest request);
}

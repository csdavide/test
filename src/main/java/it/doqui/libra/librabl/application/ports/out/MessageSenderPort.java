package it.doqui.libra.librabl.application.ports.out;

import it.doqui.libra.librabl.application.model.messaging.MessageCreation;
import it.doqui.libra.librabl.application.model.messaging.MessagingTask;

import java.util.Map;

public interface MessageSenderPort {
    String submit(MessagingTask m);
    String submit(MessageCreation m, String queue);
    String submit(String type, Map<?,?> map, int priority, long delay, String queue);
}

package it.doqui.libra.librabl.business.provider.integration.messaging;

public interface TaskProducer {
    String submit(MessagingTask m);
    String submit(MessageCreation m, String queue);
}

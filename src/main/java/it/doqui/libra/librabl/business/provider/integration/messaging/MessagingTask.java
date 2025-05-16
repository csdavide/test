package it.doqui.libra.librabl.business.provider.integration.messaging;

public interface MessagingTask extends MessageCreation {

    default String getQueueName() {
        return null;
    }

    String getTaskId();
    String getMessageType();
    Integer getPriority();
}

package it.doqui.libra.librabl.application.model.messaging;

public interface MessagingTask extends MessageCreation {

    default String getQueueName() {
        return null;
    }

    String getTaskId();
    String getMessageType();
    Integer getPriority();
}

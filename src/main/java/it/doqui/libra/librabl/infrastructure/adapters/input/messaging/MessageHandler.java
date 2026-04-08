package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.Message;

public interface MessageHandler {

    void handleMessage(Message message) throws JMSException;
    default boolean requireTenant() {
        return false;
    }

}

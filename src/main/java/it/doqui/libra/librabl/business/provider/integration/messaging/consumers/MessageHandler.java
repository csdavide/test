package it.doqui.libra.librabl.business.provider.integration.messaging.consumers;

import jakarta.jms.JMSException;
import jakarta.jms.Message;

public interface MessageHandler {

    void handleMessage(Message message) throws JMSException;
    boolean requireTenant();
    default boolean requireTracing(Message message) throws JMSException {
        return false;
    }

}

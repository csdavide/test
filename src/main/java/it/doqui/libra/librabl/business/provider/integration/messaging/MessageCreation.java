package it.doqui.libra.librabl.business.provider.integration.messaging;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

public interface MessageCreation {
    Message createMessage(JMSContext context) throws JMSException;
}

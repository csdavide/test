package it.doqui.libra.librabl.application.model.messaging;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

public interface MessageCreation {
    Message createMessage(JMSContext context) throws JMSException;
}

package it.doqui.libra.librabl.business.provider.integration.messaging.consumers;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.jms.*;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Dependent
@Unremovable
@Slf4j
public class JMSReceiver implements Runnable {
    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    MessageDispatcher dispatcher;

    @Setter
    private int priority;

    @Setter
    private String destinationName;

    @Setter
    private boolean isTopic;

    @Setter
    private long retryWaitTime;

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            String address = String.format("%s?consumer-priority=%d", destinationName, priority);
            log.info("Starting message consumer on {}", address);
            try (JMSContext context = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {

                final Destination destination;
                if (isTopic) {
                    destination = context.createTopic(address);
                } else {
                    destination = context.createQueue(address);
                }

                try (JMSConsumer consumer = context.createConsumer(destination)) {
                    while (!Thread.interrupted()) {
                        log.debug("Waiting for a new message from channel {}", destinationName);
                        Message message = consumer.receive();
                        if (message == null) {
                            // receive returns `null` if the JMSConsumer is closed
                            break;
                        }

                        try {
                            try {
                                handleMessage(message);
                            } catch (BadMessageException e) {
                                log.debug("Discarding bad message {}: {}", message.getJMSMessageID(), e.getMessage());
                                message.acknowledge();
                            } catch (WebException e) {
                                if (e.getCode() >= 500) {
                                    throw e;
                                }

                                log.debug("Discarding invalid message {}: {}", message.getJMSMessageID(), e.getMessage());
                                message.acknowledge();
                            }

                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    } // end while
                }
            } catch (JMSRuntimeException e) {
                log.error(e.getMessage());
            }

            if (!Thread.interrupted()) {
                try {
                    // wait to retry
                    log.debug("Waiting to reconnect");
                    Thread.sleep(retryWaitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // ignore
                }
            }
        }
    }

    @ActivateRequestContext
    void handleMessage(Message message) throws JMSException {
        // activate request context
        Arc.container().requestContext().activate();
        dispatcher.process(message);
        message.acknowledge();
    }
}

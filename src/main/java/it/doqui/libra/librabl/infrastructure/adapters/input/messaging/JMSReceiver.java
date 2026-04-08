package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.jms.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@IfBuildProperty(name = "libra.module.async.enabled", stringValue = "true", enableIfMissing = true)
@Dependent
@Unremovable
@Slf4j
public class JMSReceiver implements Runnable {
    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    MessageDispatcher dispatcher;

    @Setter
    @Getter
    private int index;

    @Setter
    private int priority;

    @Setter
    private String destinationName;

    @Setter
    private boolean isTopic;

    @Setter
    private long retryWaitTime;

    @Setter
    private long timeout;

    @Getter
    private String threadName;

    @Override
    public void run() {
        threadName = Thread.currentThread().getName();
        while (!Thread.interrupted()) {
            String address = String.format("%s?consumer-priority=%d", destinationName, priority);
            log.info("Starting message consumer {} on {}", index, address);
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
                        long t0 = System.currentTimeMillis();
                        Message message = consumer.receive(timeout);
                        if (message == null) {
                            // receive returns `null` if the JMSConsumer is closed or the timeout expires
                            if (System.currentTimeMillis() - t0 >= timeout) {
                                continue;
                            }

                            log.debug("No message received from channel {}: reconnecting", destinationName);
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

                                log.debug("Discarding invalid message {} (code {}): {}", message.getJMSMessageID(), e.getCode(), e.getMessage());
                                message.acknowledge();
                            }

                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    } // end while
                }
            } catch (Exception e) {
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

        log.info("Message consumer terminated");
    }

    @ActivateRequestContext
    void handleMessage(Message message) throws JMSException {
        // activate request context
        Arc.container().requestContext().activate();
        dispatcher.process(message);
        message.acknowledge();
    }
}

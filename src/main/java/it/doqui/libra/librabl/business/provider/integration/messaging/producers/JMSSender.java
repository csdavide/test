package it.doqui.libra.librabl.business.provider.integration.messaging.producers;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.quarkus.runtime.ShutdownEvent;
import it.doqui.libra.librabl.business.provider.boot.BootEvent;
import it.doqui.libra.librabl.business.provider.configure.AsyncConfig;
import it.doqui.libra.librabl.business.provider.integration.messaging.MessageCreation;
import it.doqui.libra.librabl.business.provider.integration.messaging.MessagingTask;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.concurrent.*;

@ApplicationScoped
@Slf4j
public class JMSSender implements TaskProducer, Runnable {

    @Inject
    AsyncConfig config;

    @Inject
    ConnectionFactory connectionFactory;

    private ExecutorService executorService;
    private BlockingQueue<Triple<CompletableFuture<Pair<String,Exception>>, String, MessageCreation>> requestQueue;

    void onStart(@Observes BootEvent ev) {
        var concurrency = config.producer().concurrency();
        requestQueue = new ArrayBlockingQueue<>(concurrency);
        executorService = Executors.newFixedThreadPool(concurrency, new ThreadFactoryBuilder().setNameFormat("jms-producer-%d").build());
        for (int i = 0; i < concurrency; i++) {
            executorService.submit(this);
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        executorService.shutdown();
    }

    @Override
    public String submit(MessagingTask m) {
        return submit(m, m.getQueueName());
    }

    @Override
    public String submit(MessageCreation m, String queueName) {
        if (m == null) {
            log.error("Unable to send an empty message");
            return null;
        }

        final CompletableFuture<Pair<String,Exception>> f = new CompletableFuture<>();
        try {
            requestQueue.put(new ImmutableTriple<>(f, queueName, m));
            var r = f.get();
            var e = r.getRight();
            if (e != null) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }

                throw new RuntimeException(e);
            }

            return r.getLeft();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            log.info("Starting message producer with default queue {}", config.producer().defaultQueue());
            try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                JMSProducer producer = context.createProducer();
                while (!Thread.interrupted()) {
                    log.debug("Waiting for a new request");
                    var p = requestQueue.take();
                    var f = p.getLeft();
                    try {
                        var m = p.getRight();
                        var message = m.createMessage(context);
                        final String messageId;
                        if (message != null) {
                            final String queueName;
                            if (p.getMiddle() != null) {
                                queueName = p.getMiddle();
                            } else {
                                queueName = config.producer().defaultQueue();
                            }

                            final String destinationName;
                            final Destination destination;
                            if (queueName.startsWith("topic:")) {
                                var slash = queueName.lastIndexOf('/');
                                final String topicName;
                                if (slash < 0) {
                                    topicName = queueName.substring(6);
                                } else {
                                    topicName = queueName.substring(slash + 1);
                                }

                                var topic = context.createTopic(topicName);
                                destinationName = String.format("topic://%s", topic.getTopicName());
                                destination = topic;
                            } else {
                                var queue = context.createQueue(queueName);
                                destinationName = String.format("queue://%s", queue.getQueueName());
                                destination = queue;
                            }

                            log.debug("Sending message {} to {}", m, destinationName);
                            producer.send(destination, message);
                            messageId = String.format("%s/%s", destinationName, message.getJMSMessageID());
                        } else {
                            messageId = null;
                            log.warn("Got an empty message for task {}", m);
                        }

                        f.complete(new ImmutablePair<>(messageId, null));
                    } catch (JMSRuntimeException e) {
                        requestQueue.put(p);
                        log.error("Message back into the request queue. Reconnection required: {}", e.getMessage());
                        break;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        f.complete(new ImmutablePair<>(null, e));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (!Thread.interrupted()) {
                try {
                    // wait to retry
                    log.debug("Waiting to reconnect");
                    Thread.sleep(config.producer().retryWaitTime().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // ignore
                }
            }
        }
    }
}

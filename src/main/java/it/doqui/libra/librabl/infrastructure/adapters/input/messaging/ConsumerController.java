package it.doqui.libra.librabl.infrastructure.adapters.input.messaging;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import it.doqui.libra.librabl.application.model.configuration.AsyncConfig;
import it.doqui.libra.librabl.infrastructure.platform.boot.BootEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@IfBuildProperty(name = "libra.module.async.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
public class ConsumerController implements Scheduled.SkipPredicate {

    @ConfigProperty(name = "libra.module.async.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    AsyncConfig config;

    @Inject
    Scheduler scheduler;

    private ExecutorService executorService = null;
    private List<Consumer> consumers = null;

    void onStart(@Observes BootEvent ev) {
        if (enabled) {
            int n = config.consumers().stream().mapToInt(AsyncConfig.ConsumerConfig::concurrency).sum();
            consumers = new ArrayList<>(n);
            executorService = Executors.newFixedThreadPool(n, new ThreadFactoryBuilder().setNameFormat("jms-consumer-%d").build());
            int k = 0;
            for (var c : config.consumers()) {
                for (int i = 0; i < c.concurrency(); i++) {
                    var consumer = CDI.current().select(JMSReceiver.class).get();
                    consumer.setIndex(k);
                    consumer.setPriority(c.priority());
                    consumer.setDestinationName(c.channel());
                    consumer.setTopic(c.isTopic());
                    consumer.setRetryWaitTime(c.retryWaitTime().toMillis());
                    consumer.setTimeout(c.timeout().toMillis());
                    var f = executorService.submit(consumer);
                    consumers.add(new Consumer(consumer, f));
                    k++;
                }
            }
            scheduler.resume("check-consumers");
        } else {
            scheduler.pause("check-consumers");
            log.info("Async module disabled");
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (executorService != null) {
            log.info("Shutting down async module");
            executorService.shutdown();
        }
    }

    @Scheduled(every = "${libra.module.async.check-interval:600s}", identity = "check-consumers", skipExecutionIf = ConsumerController.class)
    void checkConsumers() {
        if (consumers != null) {
            for (var c : consumers) {
                log.info("Consumer {} is {} (thread {})", c.receiver().getIndex(), c.future().isDone() ? "done" : "running", c.receiver().getThreadName());
                if (c.future().isDone()) {
                    try {
                        c.future().get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Consumer {} aborted: {}", c.receiver().getIndex(), e.getMessage(), e);
                    }

                    var f = executorService.submit(c.receiver());
                    consumers.set(c.receiver().getIndex(), new Consumer(c.receiver(), f));
                }
            }
        }
    }

    @Override
    public boolean test(ScheduledExecution execution) {
        return !enabled;
    }

    private record Consumer(JMSReceiver receiver, Future<?> future) {}

}

package it.doqui.libra.librabl.business.provider.integration.messaging.consumers;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.quarkus.runtime.ShutdownEvent;
import it.doqui.libra.librabl.business.provider.boot.BootEvent;
import it.doqui.libra.librabl.business.provider.configure.AsyncConfig;
import lombok.extern.slf4j.Slf4j;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
@Slf4j
public class ConsumerController {

    @Inject
    AsyncConfig config;

    private ExecutorService executorService;

    void onStart(@Observes BootEvent ev) {
        int n = config.consumers().stream().mapToInt(AsyncConfig.ConsumerConfig::concurrency).sum();
        executorService = Executors.newFixedThreadPool(n, new ThreadFactoryBuilder().setNameFormat("jms-consumer-%d").build());
        for (var c : config.consumers()) {
            for (int i = 0; i < c.concurrency(); i++) {
                var consumer = CDI.current().select(JMSReceiver.class).get();
                consumer.setPriority(c.priority());
                consumer.setDestinationName(c.channel());
                consumer.setTopic(c.isTopic());
                consumer.setRetryWaitTime(c.retryWaitTime().toMillis());
                executorService.submit(consumer);
            }
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        executorService.shutdown();
    }

}

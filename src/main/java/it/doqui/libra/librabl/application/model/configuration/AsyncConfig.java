package it.doqui.libra.librabl.application.model.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.List;

@ConfigMapping(prefix = "async")
public interface AsyncConfig {

    @WithName("producer-pool")
    ProducerConfig producer();
    AsyncOpConfig operations();

    @WithName("long-operations")
    AsyncOpConfig longOperations();

    @WithName("mgmt-operations")
    AsyncOpConfig mgmtOperations();

    AsyncOpConfig expirables();
    List<ConsumerConfig> consumers();

    interface ConsumerConfig {
        @WithDefault("1")
        int concurrency();

        @WithDefault("0")
        int priority();

        @WithName("retry-wait-time")
        @WithDefault("5s")
        Duration retryWaitTime();

        @WithName("timeout")
        @WithDefault("PT10M")
        Duration timeout();

        @WithDefault("tasks")
        String channel();

        @WithName("is-topic")
        @WithDefault("false")
        boolean isTopic();

        @WithName("is-for-reindex")
        @WithDefault("false")
        boolean isForReindex();
    }

    interface ProducerConfig {
        @WithDefault("1")
        int concurrency();

        @WithName("default-queue")
        @WithDefault("tasks")
        String defaultQueue();

        @WithName("events-topic")
        @WithDefault("events")
        String eventsTopic();

        @WithName("retry-wait-time")
        @WithDefault("5s")
        Duration retryWaitTime();
    }

    interface AsyncOpConfig {
        @WithName("queue")
        @WithDefault("operations")
        String queue();

        @WithDefault("0s")
        @WithName("duration-greater-than")
        Duration durationGreaterThan();
    }
}

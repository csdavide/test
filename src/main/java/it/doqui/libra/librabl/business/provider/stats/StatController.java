package it.doqui.libra.librabl.business.provider.stats;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.quarkus.runtime.ShutdownEvent;
import it.doqui.libra.librabl.business.provider.boot.BootEvent;
import it.doqui.libra.librabl.business.service.interfaces.StatService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.concurrent.*;

@ApplicationScoped
@Slf4j
public class StatController implements StatService, Runnable {

    @ConfigProperty(name = "libra.stats.buffer-size", defaultValue = "100")
    int bufferSize;

    @ConfigProperty(name = "libra.stats.wait-time", defaultValue = "10m")
    Duration waitTime;

    @ConfigProperty(name = "libra.stats.log-time", defaultValue = "10m")
    Duration logTime;

    private final StatMeasure aggregatedStatMeasure = new StatMeasure();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("stats-%d").build());
    private BlockingQueue<StatMeasure> queue;

    @PostConstruct
    void init() {
        queue = new ArrayBlockingQueue<>(bufferSize);
    }

    void onStart(@Observes BootEvent ev) {
        executorService.submit(this);
    }

    void onStop(@Observes ShutdownEvent ev) {
        executorService.shutdown();
    }

    public void add(StatMeasure m) {
        if (!queue.offer(m)) {
            log.warn("Unable to add stat measure. Stat buffer size too small.");
        }
    }

    @Override
    public StatMeasure getAggregatedStatMeasure() {
        synchronized (aggregatedStatMeasure) {
            StatMeasure m = new StatMeasure();
            m.getCounters().putAll(aggregatedStatMeasure.getCounters());
            return m;
        }
    }

    @Override
    public void run() {
        log.debug("Starting");
        long t0 = System.currentTimeMillis();
        while (!Thread.interrupted()) {
            try {
                StatMeasure m = queue.poll(waitTime.toSeconds(), TimeUnit.SECONDS);
                if (m != null) {
                    synchronized (aggregatedStatMeasure) {
                        m.getCounters().forEach((k, v) -> {
                            if (v != null) {
                                aggregatedStatMeasure.add(k, v);
                            }
                        });
                    }
                }

                long t1 = System.currentTimeMillis();
                if (t1 - t0 >= logTime.toMillis()) {
                    log.info("Stats: {}", aggregatedStatMeasure.getCounters());
                    t0 = t1;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

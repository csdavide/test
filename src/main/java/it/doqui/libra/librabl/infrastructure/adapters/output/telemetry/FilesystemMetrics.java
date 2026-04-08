package it.doqui.libra.librabl.infrastructure.adapters.output.telemetry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Startup
@ApplicationScoped
public class FilesystemMetrics {

    @ConfigProperty(name = "libra.telemetry.fs.path", defaultValue = "/appserv/quarkus")
    String path;

    @Inject
    MeterRegistry registry;

    @PostConstruct
    void init() {
        Gauge.builder("disk.free.percent", this::getFreePercent)
            .description("Free disk space percent")
            .register(registry);
    }

    public double getFreePercent() {
        java.io.File root = new java.io.File(path);
        double freePercent = 0.0;
        long total = root.getTotalSpace();
        if (total > 0) {
            long free = root.getFreeSpace();
            freePercent = ((double) free / total) * 100;
        }
        return freePercent;
    }
}

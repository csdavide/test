package it.doqui.libra.librabl.infrastructure.adapters.input.rest.providers;

import io.quarkus.runtime.configuration.DurationConverter;
import jakarta.ws.rs.ext.ParamConverter;

import java.time.Duration;

public class DurationParamConverter implements ParamConverter<Duration> {
    @Override
    public Duration fromString(String value) {
        if (value == null || value.isBlank()) return null;
        // Puoi usare il converter interno di Quarkus che supporta "10s", "5m", ecc.
        return DurationConverter.parseDuration(value);
    }

    @Override
    public String toString(Duration value) {
        return value != null ? value.toString() : null;
    }
}

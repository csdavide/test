package it.doqui.libra.librabl.domain.ports.out;

import java.time.Duration;
import java.util.Collection;

public interface FileAggregatorPort {
    void submitAggregation(String jobId, Collection<FileRequest> requests, Duration duration);
}

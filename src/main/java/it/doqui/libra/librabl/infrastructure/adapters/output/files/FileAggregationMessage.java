package it.doqui.libra.librabl.infrastructure.adapters.output.files;

import it.doqui.libra.librabl.domain.ports.out.FileRequest;

import java.time.Duration;
import java.util.Collection;

public record FileAggregationMessage (Collection<FileRequest> requests, Duration duration) {}

package it.doqui.libra.librabl.infrastructure.adapters.input.messaging.aggregation;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class FileAggregationFeedback {
    private FileAccess fileAccess;
    private int fileCount;
}

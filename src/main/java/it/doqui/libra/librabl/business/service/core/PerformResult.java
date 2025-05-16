package it.doqui.libra.librabl.business.service.core;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Set;
import java.util.function.Function;

@Getter
@Setter
@Accessors(chain = true)
@Builder
@ToString
public class PerformResult<T> {

    public enum Mode {
        NONE,
        WITHIN_TX,
        SYNC,
        ASYNC
    }

    private Mode mode;

    private T result;
    private long count;
    private Long tx;
    private Set<String> priorityUUIDs;

    public <R> PerformResult<R> map(Function<T, R> mapper) {
        var r = mapper.apply(result);
        return PerformResult.<R>builder()
            .mode(mode)
            .count(count)
            .tx(tx)
            .priorityUUIDs(priorityUUIDs)
            .result(r)
            .build();
    }
}

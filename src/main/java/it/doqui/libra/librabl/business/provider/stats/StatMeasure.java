package it.doqui.libra.librabl.business.provider.stats;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString
public class StatMeasure {
    private final Map<String,Long> counters;

    public StatMeasure() {
        this.counters = new HashMap<>();
    }

    public Long add(String name, long value) {
        return counters.compute(name, (k, v) -> (v == null ? 0L : v) + value);
    }

    public Long step(String name, long value) {
        return counters.compute(name, (k, v) -> Math.abs(value - (v == null ? 0L : v)));
    }
}

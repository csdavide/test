package it.doqui.libra.librabl.infrastructure.platform.tx;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import it.doqui.libra.librabl.foundation.DurationFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class TxReindexRequest {
    private String tenant;
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<Long> transactions;
    private boolean addOnly;
    private int priority;

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = DurationFilter.class)
    private Duration delay = Duration.ZERO;

    public TxReindexRequest() {
        this.transactions = new ArrayList<>();
    }
}

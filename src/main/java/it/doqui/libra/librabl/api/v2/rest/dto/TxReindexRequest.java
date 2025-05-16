package it.doqui.libra.librabl.api.v2.rest.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TxReindexRequest {
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<Long> transactions;
    private int priority;

    public TxReindexRequest() {
        this.transactions = new ArrayList<>();
    }
}

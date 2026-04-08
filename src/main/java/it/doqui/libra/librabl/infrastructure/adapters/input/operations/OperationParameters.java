package it.doqui.libra.librabl.infrastructure.adapters.input.operations;

import it.doqui.libra.librabl.foundation.OperationMode;
import it.doqui.libra.librabl.foundation.async.TxSupport;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class OperationParameters {
    private OperationMode mode;
    private long delay;
    private Long limit;
    private String queue;
    private TxSupport txSupport;
}

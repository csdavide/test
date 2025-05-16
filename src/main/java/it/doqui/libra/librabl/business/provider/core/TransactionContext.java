package it.doqui.libra.librabl.business.provider.core;

import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.provider.integration.indexing.ReindexTask;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Getter(AccessLevel.PACKAGE)
@ToString
class TransactionContext {
    private final ApplicationTransaction tx;
    private PerformResult.Mode mode;
    private long count;
    private final AtomicReference<ReindexTask> asyncTxRef;
    private final Set<String> priorityUUIDs;

    TransactionContext(ApplicationTransaction tx) {
        this.tx = tx;
        this.mode = PerformResult.Mode.SYNC;
        this.count = 0;
        this.asyncTxRef = new AtomicReference<>();
        this.priorityUUIDs = new HashSet<>();
    }

    public void addCount(long count) {
        this.count += count;
    }

    void setMode(PerformResult.Mode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }
}

package it.doqui.libra.librabl.infrastructure.platform.tx;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
@ToString
class TransactionGroup {
    private final Deque<TransactionContext> stack;
    private final Deque<TransactionContext> completedContexts;
    private boolean disableWithInTxMode;
    private Set<String> createdFileSet;
    private Set<String> replacedFileSet;

    TransactionGroup() {
        this.stack = new LinkedList<>();
        this.completedContexts = new LinkedList<>();
        this.disableWithInTxMode = false;
        this.createdFileSet = new HashSet<>();
        this.replacedFileSet = new HashSet<>();
    }

}

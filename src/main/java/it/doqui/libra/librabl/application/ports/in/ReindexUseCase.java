package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.foundation.TenantRef;

import java.util.Collection;

public interface ReindexUseCase {
    void syncReindexTransactions(TenantRef tenantRef, Collection<Long> transactions);
    void syncReindexNodes(TenantRef tenantRef, Collection<String> uuids);
}

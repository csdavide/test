package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.foundation.TenantRef;

import java.time.ZonedDateTime;
import java.util.Collection;

public interface ReindexService {
    void syncReindexTransactions(TenantRef tenantRef, Collection<Long> transactions);
    void syncReindexNodes(TenantRef tenantRef, Collection<String> uuids);
    void reindex(TenantRef tenantRef, long txId, String flags);
    void reindex(TenantRef tenantRef, Collection<String> transactions, String flags);
    void reindex(TenantRef tenantRef, ZonedDateTime fromDateTime, ZonedDateTime toDateTime, String flags, int blockSize, boolean addOnly);
}

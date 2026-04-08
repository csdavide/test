package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.ports.in.ReindexUseCase;
import it.doqui.libra.librabl.application.ports.out.ReindexPort;
import it.doqui.libra.librabl.foundation.TenantRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;

@ApplicationScoped
public class ReindexService implements ReindexUseCase {

    @Inject
    ReindexPort reindexPort;

    @Override
    public void syncReindexTransactions(TenantRef tenantRef, Collection<Long> transactions) {
        reindexPort.syncReindexTransactions(tenantRef, transactions);
    }

    @Override
    public void syncReindexNodes(TenantRef tenantRef, Collection<String> uuids) {
        reindexPort.syncReindexNodes(tenantRef, uuids);
    }
}

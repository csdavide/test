package it.doqui.libra.librabl.application.ports.out;

import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.application.model.jobs.responses.ReindexJobResult;

import java.util.Collection;

public interface ReindexPort {
    void syncReindexTransactions(TenantRef tenantRef, Collection<Long> transactions);
    void syncReindexNodes(TenantRef tenantRef, Collection<String> uuids);
    ReindexJobResult reindexSubTree(Vertex node, int blockSize, boolean recursive);
}

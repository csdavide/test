package it.doqui.libra.librabl.business.provider.integration.indexing;

import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.business.provider.configure.AsyncConfig;
import it.doqui.libra.librabl.business.provider.integration.indexing.impl.Indexer;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrServerException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.DEFAULT_FLAG_MASK;

@ApplicationScoped
@Slf4j
public class IndexerDelegate {

    @ConfigProperty(name = "libra.reindex.queue", defaultValue = "tasks")
    String queueName;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    Indexer indexer;

    @Inject
    TaskProducer producer;

    @Inject
    AgroalDataSource ds;

    public void execute(ApplicationTransaction tx, Set<String> includedUUIDs, boolean completed) throws IndexingException {
        indexer.reindexTransactions(null, tx.getTenant(), tx.getDbSchema(), List.of(tx.getId()), DEFAULT_FLAG_MASK, includedUUIDs, null, false, completed, false);
    }

    public void execute(ApplicationTransaction tx, final AtomicReference<ReindexTask> asyncRequiredTx) throws IndexingException {
        DBUtils.call(ds, tx.getDbSchema(), conn -> {
            indexer
                .reindex(conn, tx.getTenant(), List.of(tx.getId()), DEFAULT_FLAG_MASK,null,null,false, true, false)
                .forEach(asyncRequiredTx::set);
            return null;
        });
    }

    public void removeTransaction(TenantRef tenantRef, String tx) {
        try {
            indexer.removeAllHavingTransaction(tenantRef, tx);
        } catch (IOException | SolrServerException e) {
            throw new IndexingException(e);
        }
    }

    public void submit(ReindexTask m) {
        try {
            producer.submit(m, queueForReindex(m.getPriority()));
        } catch (RuntimeException e) {
            if (e.getCause() == null || !(e.getCause() instanceof InterruptedException)) {
                log.error("Got exception submitting async reindex (tx {}): {}",
                    m.getTxList(), Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage()));
                //TODO: schedulare un job di controllo e reindex aync delle transazioni non completate
                // oppure valutare un job ricorrente in fase di setup; in questo caso ignorare l'errore
            }
        }
    }

    private String queueForReindex(int priority) {
        return asyncConfig.consumers().stream()
            .filter(AsyncConfig.ConsumerConfig::isForReindex)
            .filter(c -> c.priority() <= priority)
            .sorted((a, b) -> -1 * Integer.compare(a.priority(), b.priority()))
            .map(AsyncConfig.ConsumerConfig::channel)
            .findFirst()
            .orElse(queueName);
    }

}

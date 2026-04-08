package it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing;

import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.application.model.configuration.AsyncConfig;
import it.doqui.libra.librabl.application.model.messaging.MessageType;
import it.doqui.libra.librabl.application.ports.out.MessageSenderPort;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;

import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.infrastructure.platform.tx.TxReindexRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.domain.model.graph.IndexingFlags.DEFAULT_FLAG_MASK;

@ApplicationScoped
@Slf4j
public class IndexerDelegate {

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    Indexer indexer;

    @Inject
    MessageSenderPort producer;

    @Inject
    AgroalDataSource ds;

    @Inject
    AuthenticationManagerPort authenticationManagerPort;

    @Inject
    SessionContext sessionContext;

    public void execute(ApplicationTransaction tx, Set<String> includedUUIDs) throws IndexingException {
        var async = sessionContext.getMode() == SessionMode.ASYNC;
        indexer.reindexTransactions(tx.getTenant(), tx.getDbSchema(), List.of(tx.getId()), DEFAULT_FLAG_MASK, includedUUIDs, null, async, false);
    }

    public void executeInTransaction(ApplicationTransaction tx, final AtomicReference<ReindexTask> asyncRequiredTx) throws IndexingException {
        var async = sessionContext.getMode() == SessionMode.ASYNC;
        DBUtils.call(ds, tx.getDbSchema(), conn -> {
            indexer
                .reindexTransactions(conn, tx.getTenant(), List.of(tx.getId()), DEFAULT_FLAG_MASK,null,null, async, false)
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

    public void submitReindex(TxReindexRequest request) {
        if (StringUtils.isNotBlank(request.getTenant())) {
            authenticationManagerPort.autenticateIfRequired(TenantRef.valueOf(request.getTenant()), true);
        }

        var queue = queueForReindex(request.getPriority());
        var delay = Optional.ofNullable(request.getDelay()).map(Duration::toMillis).orElse(0L);
        var map = Map.of(
                "tenant", sessionContext.getTenant(),
                "tx", request.getTransactions().stream().map(Object::toString).collect(Collectors.joining(",")),
                "completed", true,
                "addOnly", request.isAddOnly()
        );
        producer.submit(MessageType.REINDEX, map, request.getPriority(), delay, queue);
    }

    private String queueForReindex(int priority) {
        return asyncConfig.consumers().stream()
            .filter(AsyncConfig.ConsumerConfig::isForReindex)
            .filter(c -> c.priority() <= priority)
            .sorted((a, b) -> -1 * Integer.compare(a.priority(), b.priority()))
            .map(AsyncConfig.ConsumerConfig::channel)
            .findFirst()
            .orElse(asyncConfig.producer().defaultQueue());
    }

}

package it.doqui.libra.librabl.business.provider.integration.indexing;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.ShutdownEvent;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.TxDAO;
import it.doqui.libra.librabl.business.provider.integration.indexing.impl.Indexer;
import it.doqui.libra.librabl.business.provider.integration.messaging.TaskProducer;
import it.doqui.libra.librabl.business.provider.security.AuthenticationManager;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.interfaces.ReindexService;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static it.doqui.libra.librabl.business.provider.integration.messaging.MessagePriority.LOW_PRIORITY;
import static it.doqui.libra.librabl.business.provider.integration.messaging.MessagePriority.STANDARD_PRIORITY;

@ApplicationScoped
@Slf4j
public class ReindexController implements ReindexService {

    @ConfigProperty(name = "libra.reindex.request-pool-size", defaultValue = "4")
    int reindexRequestsPoolSize;

    @ConfigProperty(name = "libra.reindex.queue", defaultValue = "tasks")
    String queueName;

    @Inject
    AgroalDataSource ds;

    @Inject
    TxDAO txDAO;

    @Inject
    AuthenticationManager authenticationService;

    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    TaskProducer producer;

    @Inject
    Indexer indexer;

    @Inject
    NodeDAO nodeDAO;

    private ExecutorService reindexService;

    @PostConstruct
    void init() {
        reindexService = Executors.newFixedThreadPool(reindexRequestsPoolSize);
    }

    void onStop(@Observes ShutdownEvent ev) {
        reindexService.shutdown();
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.MANAGEMENT)
    public void reindex(TenantRef tenantRef, long txId, String flags) {
        authenticationService.autenticateIfRequired(tenantRef, true);

        final ApplicationTransaction tx;
        tx = txDAO.findTransactionById(txId)
            .orElseThrow(() -> new NotFoundException("Transaction not found"));

        ReindexTask m = new ReindexTask();
        m.setTaskId(UserContextManager.getContext().getOperationId());
        m.setFlags(IndexingFlags.parse(flags));
        m.setTenant(UserContextManager.getContext().getTenantRef().toString());
        m.setTx(tx.getId());
        m.setCompleted(true);
        m.setPriority(STANDARD_PRIORITY);
        m.setQueueName(queueName);

        producer.submit(m);
    }

    @Override
    public void syncReindexTransactions(TenantRef tenantRef, Collection<Long> transactions) {
        tenantRef = authenticationService.autenticateIfRequired(tenantRef, true);
        indexer.reindexTransactions(null, tenantRef.toString(), null, transactions, IndexingFlags.FULL_FLAG_MASK, null, null, true, true, false);
    }

    @Override
    public void syncReindexNodes(TenantRef tenantRef, Collection<String> uuids) {
        tenantRef = authenticationService.autenticateIfRequired(tenantRef, true);
        var m = nodeDAO.mapNodesInUUIDs(uuids);
        for (var uuid : uuids) {
            var n = m.get(uuid);
            if (n != null) {
                indexer.reindexSubTree(null, tenantRef.toString(), n.getId(), IndexingFlags.FULL_FLAG_MASK, false, 40, false);
            }
        }
    }

    @Override
    @Traceable(traceAllParameters = true, category = TraceCategory.MANAGEMENT)
    public void reindex(TenantRef tenantRef, Collection<String> transactions, String flags) {
        authenticationService.autenticateIfRequired(tenantRef, true);

        ReindexTask m = new ReindexTask();
        m.setTaskId(UserContextManager.getContext().getOperationId());
        m.setFlags(IndexingFlags.parse(flags));
        m.setTenant(UserContextManager.getContext().getTenantRef().toString());
        m.setCompleted(true);
        m.setPriority(LOW_PRIORITY);
        m.setQueueName(queueName);

        var strings = new ArrayList<String>();
        for (String s : transactions) {
            if (StringUtils.isNumeric(s)) {
                m.getTxList().add(Long.parseLong(s));
            } else {
                strings.add(s);
            }
        }

        if (!strings.isEmpty()) {
            txDAO.listTransactions(strings)
                .stream()
                .map(ApplicationTransaction::getId)
                .forEach(m.getTxList()::add);
        }

        producer.submit(m);
    }

    @Override
    public void reindex(TenantRef tenantRef, ZonedDateTime fromDateTime, ZonedDateTime toDateTime, String flags, int blockSize, boolean addOnly) {
        tenantRef = authenticationService.autenticateIfRequired(tenantRef, true);

        final var tenant = tenantRef.toString();
        log.info("Start indexing tenant {} from {} to {}", tenant, fromDateTime, toDateTime);
        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            log.debug("Queue connected");
            Queue queue = context.createQueue(queueName);
            JMSProducer producer = context.createProducer();
            log.debug("Getting database connection");

            DBUtils.call(ds, UserContextManager.getContext().getDbSchema(), conn -> {
                final String sql = """
                    select id from ecm_transactions\s
                    where tenant = ? and created_at between ? and ?\s
                    order by created_at
                    """;
                log.debug("Preparing query");
                try (PreparedStatement stmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    stmt.setFetchSize(blockSize);
                    stmt.setString(1, tenant);
                    stmt.setTimestamp(2, new Timestamp(fromDateTime.toInstant().toEpochMilli()));
                    stmt.setTimestamp(3, new Timestamp(toDateTime.toInstant().toEpochMilli()));
                    try (ResultSet rs = stmt.executeQuery()) {
                        final List<Long> block = new ArrayList<>(blockSize);
                        long count = 0;

                        final Callable<Void> sendTask = () -> {
                            ReindexTask m = new ReindexTask();
                            m.setFlags(IndexingFlags.parse(Objects.requireNonNullElse(flags, "1111")));
                            m.setTenant(tenant);
                            m.getTxList().addAll(block);
                            m.setCompleted(true);
                            m.setAddOnly(addOnly);
                            m.setPriority(LOW_PRIORITY);

                            var message = m.createMessage(context);
                            log.debug("Sending message tenant {} tx {}", tenant, m.getTxList());
                            producer.send(queue, message);
                            block.clear();

                            return null;
                        };

                        while (rs.next()) {
                            long tx = rs.getLong("id");
                            block.add(tx);

                            if (block.size() >= blockSize) {
                                sendTask.call();
                            }

                            count++;
                        } // end while

                        if (!block.isEmpty()) {
                            sendTask.call();
                        }

                        log.info("{} transactions submitted", count);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return null;
            });
        }
    }

}

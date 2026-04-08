package it.doqui.libra.librabl.infrastructure.platform.tx;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionExceptionResult;
import it.doqui.libra.librabl.infrastructure.platform.boot.Bootstrapper;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.TxDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing.IndexerDelegate;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing.IndexingException;
import it.doqui.libra.librabl.infrastructure.adapters.output.solr.indexing.ReindexTask;
import it.doqui.libra.librabl.infrastructure.platform.security.AuthenticationManager;
import it.doqui.libra.librabl.domain.model.session.UserContext;

import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.domain.ports.out.ContentRepository;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.infrastructure.platform.security.SessionContextHolder;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static it.doqui.libra.librabl.domain.model.graph.IndexingFlags.DEFAULT_FLAG_MASK;
import static it.doqui.libra.librabl.application.model.messaging.MessagePriority.HIGH_PRIORITY;
import static it.doqui.libra.librabl.application.model.messaging.MessagePriority.STANDARD_PRIORITY;

@ApplicationScoped
@Slf4j
public class TransactionManager implements TransactionManagerPort {

    @ConfigProperty(name = "libra.reindex.max-synchronous-count", defaultValue = "1000")
    long synchronousReindexMaxCount;

    @ConfigProperty(name = "libra.transaction.timeout.sync", defaultValue = "60s")
    Duration syncTxTimeout;

    @ConfigProperty(name = "libra.transaction.timeout.async", defaultValue = "600s")
    Duration asyncTxTimeout;

    @Inject
    IndexerDelegate indexerDelegate;

    @Inject
    TxDAO txDAO;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @Inject
    ContentRepository contentStoreManager;

    @Inject
    AuthenticationManager authenticationManager;

    @Inject
    SessionContextHolder sessionContext;

    private final ThreadLocal<TransactionGroup> txGroupLocal = new ThreadLocal<>();

    @Override
    public <T> T doOnOtherContext(Supplier<T> f) {
        UserContext _ctx = sessionContext.getUserContext();
        try {
            return f.get();
        } finally {
            if (_ctx != null) {
                sessionContext.setUserContext(_ctx);
            }
        }
    }

    @Override
    public <T> T doAsUser(AuthorityRef authorityRef, Supplier<T> f) {
        UserContext _ctx = null;
        try {
            if (authorityRef != null) {
                _ctx = sessionContext.getUserContext();
                authenticationManager.authenticateUserOnBehalfOf(authorityRef, _ctx);
            }

            return f.get();
        } finally {
            if (_ctx != null) {
                sessionContext.setUserContext(_ctx);
            }
        }
    }

    @Override
    public <T> T doAsAdmin(Supplier<T> f) {
        UserContext _ctx = sessionContext.getUserContext();
        try {
            authenticationManager.loginAsAdmin(_ctx);
            return f.get();
        } finally {
            if (_ctx != null) {
                sessionContext.setUserContext(_ctx);
            }
        }
    }

    @Override
    public <T> T doOnTemp(Supplier<T> f) {
        var temp = sessionContext.getTenantData().map(TenantData::getTemp).orElse(null);
        if (temp == null || Strings.CI.equals(temp, sessionContext.getTenant())) {
            return f.get();
        } else {
            return doAsUser(new AuthorityRef("admin", TenantRef.valueOf(temp)), f);
        }
    }

    @Override
    public <T> T doOnTenant(TenantRef tenantRef, Supplier<T> f) {
        final var currentTenant = sessionContext.getTenant();
        if (StringUtils.isBlank(tenantRef.toString()) || Strings.CS.equals(tenantRef.toString(), currentTenant)) {
            return f.get();
        }

        return doAsUser(new AuthorityRef("admin", tenantRef), f);
    }

    @Override
    public String getInstanceId() {
        return Arc.container().select(Bootstrapper.class).get().getInstanceId();
    }

    @Override
    public <T> T requireNew(Function<ApplicationTransaction, PerformResult<T>> f) {
        return requireNew(() -> performNew(f));
    }

    @Override
    public <T> T performNew(Function<ApplicationTransaction, PerformResult<T>> f) {
        var txGroup = txGroupLocal.get();
        if (txGroup == null) {
            return requireNew(() -> performNew(f));
        }

        return execNewTransaction(txGroup, f);
    }

    @Override
    public <T> T perform(Supplier<T> f) {
        var txGroup = txGroupLocal.get();
        if (txGroup == null) {
            return requireNew(f);
        }

        return f.get();
    }

    @Override
    public <T> T requireNew(Supplier<T> f) {
        var txGroup = txGroupLocal.get();
        if (txGroup != null) {
            try {
                txGroupLocal.remove();
                log.info("New nested database transaction group created");
                return requireNew(f);
            } finally {
                txGroupLocal.set(txGroup);
                log.info("Back to the previous database transaction group");
            }
        }

        try {
            final var finalTxGroup = new TransactionGroup();
            final var timeout = sessionContext.getMode() == SessionMode.ASYNC ? asyncTxTimeout : syncTxTimeout;
            txGroupLocal.set(finalTxGroup);
            log.debug("Transaction group initialized");

            final var finalResult = QuarkusTransaction.requiringNew().timeout((int) timeout.toSeconds()).exceptionHandler(throwable -> {
                var size = finalTxGroup.getStack().size() + finalTxGroup.getCompletedContexts().size();
                log.error("Aborting transaction group. {} transactions to abort: {}", size, throwable.getMessage());
                abort(finalTxGroup.getStack());
                abort(finalTxGroup.getCompletedContexts());
                cleanTemporaryStreams(finalTxGroup.getCreatedFileSet());

                return TransactionExceptionResult.ROLLBACK;
            }).call(() -> {
                var result = f.get();

                for (var tc : finalTxGroup.getCompletedContexts()) {
                    if (tc.getMode() == PerformResult.Mode.WITHIN_TX) {
                        if (finalTxGroup.isDisableWithInTxMode()) {
                            log.debug("Switching transaction {} from WITHIN_TX to SYNC mode", tc.getTx().getId());
                            tc.setMode(PerformResult.Mode.SYNC);
                        } else {
                            log.debug("Indexing with-in transaction {}", tc.getTx().getId());
                            indexerDelegate.executeInTransaction(tc.getTx(), tc.getAsyncTxRef());
                        }
                    }
                }

                return result;
            });
            //COMMITTED

            if (!finalTxGroup.getStack().isEmpty()) {
                log.warn("Transaction group successfully committed with uncompleted {} transactions", finalTxGroup.getStack().size());
            } else {
                log.debug("Transaction group successfully committed");
            }

            var reindexMap = new HashMap<String, ReindexTask>();
            var syncContexts = new LinkedList<TransactionContext>();
            for (var tc : finalTxGroup.getCompletedContexts()) {
                var asyncTx = tc.getAsyncTxRef().get();
                if (asyncTx != null) {
                    log.debug("Moving to async required transactions {} on tenant {}", asyncTx.getTxList(), asyncTx.getTenant());
                    addAsyncTx(reindexMap, asyncTx);
                }

                if (tc.getMode() == null) {
                    tc.setMode(PerformResult.Mode.SYNC);
                }

                switch (tc.getMode()) {
                    case ASYNC -> addAsyncTx(reindexMap, tc, null);
                    case SYNC -> syncContexts.add(tc);
                }
            } // end for

            if (!syncContexts.isEmpty()) {
                QuarkusTransaction.suspendingExisting().call(() -> {
                    for (var tc : syncContexts) {
                        try {
                            var count = tc.getCount();
                            if (count <= 0) {
                                count = txDAO.count(tc.getTx(), synchronousReindexMaxCount);
                            }

                            if (count > synchronousReindexMaxCount || count <= 0) {
                                if (!tc.getPriorityUUIDs().isEmpty()) {
                                    log.debug("Indexing sync transaction {} priority uuids {}", tc.getTx().getId(), tc.getPriorityUUIDs());
                                    indexerDelegate.execute(tc.getTx(), tc.getPriorityUUIDs());
                                }

                                log.debug("Sync transaction {} moved to async list", tc.getTx().getId());
                                addAsyncTx(reindexMap, tc, HIGH_PRIORITY);
                            } else {
                                log.debug("Indexing sync transaction {}", tc.getTx().getId());
                                indexerDelegate.execute(tc.getTx(), null);
                            }
                        } catch (IndexingException e) {
                            addAsyncTx(reindexMap, tc, null);
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            addAsyncTx(reindexMap, tc, null);
                        }
                    }

                    return null;
                });
            }

            for (var contentUrl : finalTxGroup.getReplacedFileSet()) {
                if (finalTxGroup.getCreatedFileSet().contains(contentUrl)) {
                    try {
                        log.debug("Removing replaced content {}", contentUrl);
                        contentStoreManager.delete(contentUrl);
                    } catch (IOException | RuntimeException e) {
                        log.warn(String.format("Got exception while deleting replaced content url %s: %s", contentUrl, e.getMessage()), e);
                    }
                } else {
                    log.warn("Replaced content {} not found in created set", contentUrl);
                }
            }

            if (!reindexMap.isEmpty()) {
                reindexMap.forEach((tenant, m) -> {
                    if (!m.getTxList().isEmpty()) {
                        log.debug("Submitting async transactions {} for tenant {}", m.getTxList(), m.getTenant());
                        indexerDelegate.submit(m);
                    }
                });
            }

            log.debug("Transaction group successfully completed");
            return finalResult;
        } finally {
            log.debug("Transaction group closed");
            txGroupLocal.remove();
        }
    }

    @Override
    public void cleanTemporaryStreams(Collection<String> contentUrlSet) {
        for (var contentUrl : contentUrlSet) {
            try {
                contentStoreManager.delete(contentUrl);
            } catch (IOException | RuntimeException e) {
                log.warn(String.format("Got exception while deleting aborted content url %s: %s", contentUrl, e.getMessage()), e);
            }
        }
    }

    private void addAsyncTx(final Map<String, ReindexTask> reindexMap, final TransactionContext tc, Integer priority) {
        reindexMap.compute(tc.getTx().getTenant(), (k,m) -> {
            if (m == null) {
                m = new ReindexTask();
                m.setTaskId(sessionContext.getOperationId());
                m.setFlags(DEFAULT_FLAG_MASK);
                m.setTenant(k);
                m.setCompleted(true);
                m.setPriority(STANDARD_PRIORITY);
            }

            m.getTxList().add(tc.getTx().getId());
            if (priority != null) {
                m.setPriority(priority);
            }
            return m;
        });
    }

    private void addAsyncTx(final Map<String, ReindexTask> reindexMap, ReindexTask task) {
        reindexMap.compute(task.getTenant(), (k,m) -> {
            if (m == null) {
                m = new ReindexTask();
                m.setTaskId(sessionContext.getOperationId());
                m.setFlags(DEFAULT_FLAG_MASK);
                m.setTenant(k);
                m.setCompleted(true);
                m.setPriority(STANDARD_PRIORITY);
            }

            m.getTxList().addAll(task.getTxList());
            if (task.getPriority() != null && task.getPriority() > m.getPriority()) {
                m.setPriority(task.getPriority());
            }
            return m;
        });
    }

    @Override
    public <T> T perform(Function<ApplicationTransaction, PerformResult<T>> f) {
        var txGroup = txGroupLocal.get();
        if (txGroup == null) {
            return requireNew(() -> performNew(f));
        }

        var stack = txGroup.getStack();
        if (stack.isEmpty()) {
            return execNewTransaction(txGroup, f);
        }

        var tc = stack.getFirst();
        var tx = tc.getTx();
        if (!Strings.CI.equals(tx.getTenant(), sessionContext.getTenant())) {
            return execNewTransaction(txGroup, f);
        }

        log.debug("Executing block within tx {}", tx.getId());
        var result = result(tc, f.apply(tc.getTx()));
        log.debug("Terminated block within tx {}", tx.getId());
        return result;
    }

    private <T> T result(TransactionContext tc, PerformResult<T> rx) {
        tc.addCount(rx.getCount());
        if (rx.getPriorityUUIDs() != null) {
            tc.getPriorityUUIDs().addAll(rx.getPriorityUUIDs());
        }

        return rx.getResult();
    }

    @Override
    public <T> T call(Function<ApplicationTransaction, T> f) {
        return perform(tx -> PerformResult.<T>builder().result(f.apply(tx)).build());
    }

    @Override
    public <T> T connection(Function<Connection, T> f) {
        return DBUtils.call(ds, sessionContext.getUserContext().getDbSchema(), f);
    }

    private void abort(Deque<TransactionContext> list) {
        while (!list.isEmpty()) {
            var element = list.removeFirst();
            var tx = element.getTx();
            indexerDelegate.removeTransaction(TenantRef.valueOf(tx.getTenant()), String.valueOf(tx.getId()));
            log.error("Tx {} rollback", tx.getId());
        }
    }

    private <T> T execNewTransaction(TransactionGroup txGroup, Function<ApplicationTransaction, PerformResult<T>> f) {
        var tx = txDAO.createTransaction();
        try {
            var tc = new TransactionContext(tx);
            txGroup.getStack().addFirst(tc);
            log.debug("Tx {} begin (mode {}, tenant {})", tx.getId(), sessionContext.getMode(), tx.getTenant());
            var rx = f.apply(tc.getTx());
            var mode = Optional.ofNullable(rx.getMode()).orElse(PerformResult.Mode.SYNC);
            if (sessionContext.getMode() == SessionMode.ASYNC && mode.equals(PerformResult.Mode.WITHIN_TX)) {
                tc.setMode(PerformResult.Mode.SYNC);
            } else {
                tc.setMode(mode);
            }

            var result = result(tc, rx);
            log.debug("Tx {} end", tx.getId());
            return result;
        } catch (RuntimeException e) {
            log.error("Tx {} failed: {}", tx.getId(), e.getMessage(), e);
            throw e;
        } finally {
            var tc  = txGroup.getStack().removeFirst();
            txGroup.getCompletedContexts().add(tc);
        }
    }

    @Override
    public TransactionContextOptions options() {
        return new TransactionContextOptionsImpl(txGroupLocal.get());
    }

    private record TransactionContextOptionsImpl(TransactionGroup txGroup) implements TransactionContextOptions {

        @Override
        public void disableWithInTxMode() {
            if (txGroup != null) {
                txGroup.setDisableWithInTxMode(true);
            }
        }

        @Override
        public void registerCreatedContentUrl(String contentUrl) {
            if (txGroup != null && StringUtils.isNotBlank(contentUrl)) {
                txGroup.getCreatedFileSet().add(contentUrl);
            }
        }

        @Override
        public Set<String> getCreatedFileSet() {
            return txGroup != null ? Set.copyOf(txGroup.getCreatedFileSet()) : Collections.emptySet();
        }

        @Override
        public void registerReplacedContentUrl(String contentUrl) {
            if (txGroup != null && StringUtils.isNotBlank(contentUrl)) {
                txGroup.getReplacedFileSet().add(contentUrl);
            }
        }

        @Override
        public Set<String> getReplacedFileSet() {
            return txGroup != null ? Set.copyOf(txGroup.getReplacedFileSet()) : Collections.emptySet();
        }

        @Override
        public void setMode(PerformResult.Mode mode) {
            if (txGroup != null) {
                var stack = txGroup.getStack();
                if (!stack.isEmpty()) {
                    var tc = stack.getFirst();
                    tc.setMode(mode);
                }
            }

            throw new IllegalStateException("No transaction context available");
        }
    }
}

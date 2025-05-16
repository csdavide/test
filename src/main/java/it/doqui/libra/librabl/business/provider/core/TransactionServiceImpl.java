package it.doqui.libra.librabl.business.provider.core;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionExceptionResult;
import it.doqui.libra.librabl.business.provider.boot.Bootstrapper;
import it.doqui.libra.librabl.business.provider.data.dao.TxDAO;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexerDelegate;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingException;
import it.doqui.libra.librabl.business.provider.integration.indexing.ReindexTask;
import it.doqui.libra.librabl.business.provider.security.AuthenticationManager;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.ContentStoreService;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.DEFAULT_FLAG_MASK;
import static it.doqui.libra.librabl.business.provider.integration.messaging.MessagePriority.HIGH_PRIORITY;
import static it.doqui.libra.librabl.business.provider.integration.messaging.MessagePriority.STANDARD_PRIORITY;

@ApplicationScoped
@Slf4j
public class TransactionServiceImpl implements TransactionService {

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
    ContentStoreService contentStoreManager;

    @Inject
    AuthenticationManager authenticationManager;

    private final ThreadLocal<TransactionGroup> txGroupLocal = new ThreadLocal<>();

    @Override
    public <T> T doAsUser(AuthorityRef authorityRef, Supplier<T> f) {
        UserContext _ctx = null;
        try {
            if (authorityRef != null) {
                _ctx = UserContextManager.getContext();
                authenticationManager.authenticateUserOnBehalfOf(authorityRef, _ctx);
            }

            return f.get();
        } finally {
            if (_ctx != null) {
                UserContextManager.setContext(_ctx);
            }
        }
    }

    @Override
    public <T> T doAsAdmin(Supplier<T> f) {
        UserContext _ctx = UserContextManager.getContext();
        try {
            authenticationManager.loginAsAdmin(_ctx);
            return f.get();
        } finally {
            if (_ctx != null) {
                UserContextManager.setContext(_ctx);
            }
        }
    }

    @Override
    public <T> T doOnTemp(Supplier<T> f) {
        var temp = UserContextManager.getTenantData().map(TenantData::getTemp).orElse(null);
        if (temp == null || StringUtils.equalsIgnoreCase(temp, UserContextManager.getTenant())) {
            return f.get();
        } else {
            return doAsUser(new AuthorityRef("admin", TenantRef.valueOf(temp)), f);
        }
    }

    @Override
    public <T> T doOnTenant(TenantRef tenantRef, Supplier<T> f) {
        final var currentTenant = UserContextManager.getTenant();
        if (StringUtils.isBlank(tenantRef.toString()) || StringUtils.equals(tenantRef.toString(), currentTenant)) {
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
            final var tenantRef = UserContextManager.getContext().getTenantRef();
            final var finalTxGroup = new TransactionGroup();
            final var timeout = UserContextManager.getContext().getMode() == UserContext.Mode.ASYNC ? asyncTxTimeout : syncTxTimeout;
            txGroupLocal.set(finalTxGroup);
            log.debug("Transaction group initialized");

            final var finalResult = QuarkusTransaction.requiringNew().timeout((int) timeout.toSeconds()).exceptionHandler(throwable -> {
                var size = finalTxGroup.getStack().size() + finalTxGroup.getCompletedContexts().size();
                log.error("Aborting transaction group. {} transactions to abort: {}", size, throwable.getMessage());
                abort(tenantRef, finalTxGroup.getStack());
                abort(tenantRef, finalTxGroup.getCompletedContexts());

                for (var contentUrl : finalTxGroup.getCreateFileSet()) {
                    try {
                        contentStoreManager.delete(contentUrl);
                    } catch (IOException | RuntimeException e) {
                        log.warn(String.format("Got exception while deleting aborted content url %s: %s", contentUrl, e.getMessage()), e);
                    }
                }

                return TransactionExceptionResult.ROLLBACK;
            }).call(() -> {
                var result = f.get();

                var flushed = false;
                for (var tc : finalTxGroup.getCompletedContexts()) {
                    if (tc.getMode() == PerformResult.Mode.WITHIN_TX) {
                        if (finalTxGroup.isDisableWithInTxMode()) {
                            tc.setMode(PerformResult.Mode.SYNC);
                        } else {
                            if (!flushed) {
                                log.debug("Flushing and performing within transaction indexing");
                                flushed = true;
                            }

                            log.debug("Indexing with-in transaction {}", tc.getTx().getId());
                            indexerDelegate.execute(tc.getTx(), tc.getAsyncTxRef());
                        }
                    }
                }

                return result;
            });

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
                    log.debug("Submitting async required transactions {} on tenant {}", asyncTx.getTxList(), asyncTx.getTenant());
                    indexerDelegate.submit(asyncTx);
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
                                count = txDAO.count(tc.getTx());
                            }

                            if (count > synchronousReindexMaxCount || count <= 0) {
                                if (!tc.getPriorityUUIDs().isEmpty()) {
                                    log.debug("Indexing sync transaction {} priority uuids {}", tc.getTx().getId(), tc.getPriorityUUIDs());
                                    indexerDelegate.execute(tc.getTx(), tc.getPriorityUUIDs(), false);
                                }

                                log.debug("Sync transaction {} moved to async list", tc.getTx().getId());
                                addAsyncTx(reindexMap, tc, HIGH_PRIORITY);
                            } else {
                                log.debug("Indexing sync transaction {}", tc.getTx().getId());
                                indexerDelegate.execute(tc.getTx(), null, true);
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

    private void addAsyncTx(final Map<String, ReindexTask> reindexMap, final TransactionContext tc, Integer priority) {
        reindexMap.compute(tc.getTx().getTenant(), (k,m) -> {
            if (m == null) {
                m = new ReindexTask();
                m.setTaskId(UserContextManager.getContext().getOperationId());
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
        if (!StringUtils.equalsIgnoreCase(tx.getTenant(), UserContextManager.getTenant())) {
            return execNewTransaction(txGroup, f);
        }

        log.debug("Executing block within tx {}", tx.getId());
        var result = execInTransaction(tc, f);
        log.debug("Terminated block within tx {}", tx.getId());
        return result;
    }

    @Override
    public <T> T call(Function<ApplicationTransaction, T> f) {
        return perform(tx -> PerformResult.<T>builder().result(f.apply(tx)).build());
    }

    @Override
    public <T> T connection(Function<Connection, T> f) {
        return DBUtils.call(ds, UserContextManager.getContext().getDbSchema(), f);
    }

    private void abort(TenantRef tenantRef, Deque<TransactionContext> list) {
        while (!list.isEmpty()) {
            var element = list.removeFirst();
            var tx = element.getTx();
            indexerDelegate.removeTransaction(TenantRef.valueOf(tx.getTenant()), tx.getUuid());
            log.error("Tx {} ({}) rollback", tx.getId(), tx.getUuid());
        }
    }

    private <T> T execInTransaction(TransactionContext tc, Function<ApplicationTransaction, PerformResult<T>> f) {
        var rx = f.apply(tc.getTx());
        tc.addCount(rx.getCount());
        if (rx.getPriorityUUIDs() != null) {
            tc.getPriorityUUIDs().addAll(rx.getPriorityUUIDs());
        }

        if (UserContextManager.getContext().getMode() == UserContext.Mode.ASYNC) {
            tc.setMode(PerformResult.Mode.ASYNC);
        } else if (rx.getMode() != null && tc.getMode() == null) {
            tc.setMode(rx.getMode());
        }

        return rx.getResult();
    }

    private <T> T execNewTransaction(TransactionGroup txGroup, Function<ApplicationTransaction, PerformResult<T>> f) {
        var tx = txDAO.createTransaction();
        try {
            var tc = new TransactionContext(tx);
            txGroup.getStack().addFirst(tc);
            log.debug("Tx {} begin (mode {}, uuid {}, tenant {})", tx.getId(), UserContextManager.getContext().getMode(), tx.getUuid(), tx.getTenant());
            var result = execInTransaction(tc, f);
            log.debug("Tx {} end", tx.getId());
            return result;
        } catch (RuntimeException e) {
            log.error(String.format("Tx %d failed: %s", tx.getId(), e.getMessage()), e);
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
                txGroup.getCreateFileSet().add(contentUrl);
            }
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

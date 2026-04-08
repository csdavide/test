package it.doqui.libra.librabl.domain.ports.out;

import it.doqui.libra.librabl.infrastructure.platform.tx.TransactionContextOptions;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;

import java.sql.Connection;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TransactionManagerPort {
    <T> T connection(Function<Connection, T> f);
    TransactionContextOptions options();
    <T> T perform(Supplier<T> f);
    <T> T perform(Function<ApplicationTransaction, PerformResult<T>> f);
    <T> T requireNew(Function<ApplicationTransaction, PerformResult<T>> f);
    <T> T performNew(Function<ApplicationTransaction, PerformResult<T>> f);
    <T> T doOnOtherContext(Supplier<T> f);
    <T> T doAsUser(AuthorityRef authorityRef, Supplier<T> f);
    <T> T doAsAdmin(Supplier<T> f);
    <T> T requireNew(Supplier<T> f);
    <T> T call(Function<ApplicationTransaction, T> f);
    <T> T doOnTemp(Supplier<T> f);
    <T> T doOnTenant(TenantRef tenantRef, Supplier<T> f);
    String getInstanceId();
    void cleanTemporaryStreams(Collection<String> contentUrlSet);
}

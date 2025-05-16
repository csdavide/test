package it.doqui.libra.librabl.business.service.core;

import io.quarkus.arc.Arc;
import it.doqui.libra.librabl.business.provider.core.TransactionContextOptions;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;

import java.sql.Connection;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TransactionService {

    static TransactionService current() {
        return Arc.container().select(TransactionService.class).get();
    }

    <T> T connection(Function<Connection, T> f);
    TransactionContextOptions options();
    <T> T perform(Function<ApplicationTransaction, PerformResult<T>> f);
    <T> T requireNew(Function<ApplicationTransaction, PerformResult<T>> f);
    <T> T performNew(Function<ApplicationTransaction, PerformResult<T>> f);
    <T> T doAsUser(AuthorityRef authorityRef, Supplier<T> f);
    <T> T doAsAdmin(Supplier<T> f);
    <T> T requireNew(Supplier<T> f);
    <T> T call(Function<ApplicationTransaction, T> f);
    <T> T doOnTemp(Supplier<T> f);
    <T> T doOnTenant(TenantRef tenantRef, Supplier<T> f);
    String getInstanceId();
}

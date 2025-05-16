package it.doqui.libra.librabl.business.provider.management;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Unremovable;
import it.doqui.libra.librabl.business.provider.integration.messaging.consumers.MessageHandler;
import it.doqui.libra.librabl.business.provider.integration.solr.SolrManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@ApplicationScoped
@Unremovable
public class TransactionCleanerJob implements MessageHandler {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @Inject
    SolrManager solrManager;

    @Override
    public void handleMessage(Message message) throws JMSException {
        log.info("Transaction cleaner job started");
        DBUtils.call(ds, masterSchema, conn -> {
            try {
                var txSql = """
                    select t.id,t.tenant,t.uuid from %s.ecm_transactions t\s
                    where t.tenant = ? and t.ck_required\s
                    order by t.tenant\s
                    limit 1000
                    """;

                var nodeSql = """
                    select n.tx, count(*)\s
                    from %s.ecm_nodes n\s
                    where n.tx = any (?)\s
                    group by n.tx
                    """;

                var archivedSql = """
                    select n.tx, count(*)\s
                    from %s.ecm_archived_nodes n\s
                    where n.tx = any (?)\s
                    group by n.tx
                    """;

                var removedSql = """
                    select n.tx, count(*)\s
                    from %s.ecm_removed_nodes n\s
                    where n.tx = any (?)\s
                    group by n.tx
                    """;

                var sgSql = """
                    select n.tx, count(*)\s
                    from %s.ecm_security_groups n\s
                    where n.tx = any (?)\s
                    group by n.tx
                    """;

                var resetTxSql = """
                    update %s.ecm_transactions set ck_required = false\s
                    where id = any (?)
                    """;

                var deleteTxSql = """
                    delete from %s.ecm_transactions\s
                    where id = any (?)
                    """;

                try (
                    var tenantStmt = conn.createStatement();
                    ResultSet tenantRs = tenantStmt.executeQuery("""
                        select t.tenant,t.schema_name from ecm_tenants t\s
                        where not coalesce((t.data->'migration')::bool,false)
                        """))
                {
                    while (tenantRs.next()) {
                        var tenant = tenantRs.getString("tenant");
                        var schemaName = tenantRs.getString("schema_name");
                        log.info("Finding erasable transactions of tenant {} in schema {}", tenant, schemaName);
                        var txMap = new HashMap<Long, ApplicationTransaction>();
                        var usedTxSet = new HashSet<Long>();
                        try (var txStmt = conn.prepareStatement(String.format(txSql, schemaName))) {
                            txStmt.setString(1, tenant);
                            try (ResultSet txRs = txStmt.executeQuery()) {
                                while (txRs.next()) {
                                    var tx = new ApplicationTransaction();
                                    tx.setId(txRs.getLong("id"));
                                    tx.setTenant(txRs.getString("tenant"));
                                    tx.setUuid(txRs.getString("uuid"));
                                    txMap.put(tx.getId(), tx);
                                }
                            }
                        } // end try txStmt

                        filter(conn, schemaName, nodeSql, txMap, usedTxSet);
                        filter(conn, schemaName, archivedSql, txMap, usedTxSet);
                        filter(conn, schemaName, removedSql, txMap, usedTxSet);
                        filter(conn, schemaName, sgSql, txMap, usedTxSet);

                        if (!usedTxSet.isEmpty()) {
                            try (var stmt = conn.prepareStatement(String.format(resetTxSql, schemaName))) {
                                stmt.setArray(1, conn.createArrayOf("INTEGER", usedTxSet.toArray(new Long[0])));
                                var n = stmt.executeUpdate();
                                log.info("{} used transactions reset in tenant {}", n, tenant);
                            }
                        }

                        if (!txMap.isEmpty()) {
                            solrManager.deleteTransactions(TenantRef.valueOf(tenant), txMap.values().stream().map(ApplicationTransaction::getId).map(String::valueOf).toList());
                            try (var stmt = conn.prepareStatement(String.format(deleteTxSql, schemaName))) {
                                stmt.setArray(1, conn.createArrayOf("INTEGER", txMap.keySet().toArray(new Long[0])));
                                var n = stmt.executeUpdate();
                                log.info("{} deleted transactions in tenant {}", n, tenant);
                            }
                        } else {
                            log.info("no transaction to delete in tenant {}", tenant);
                        }
                    }
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }

            return null;
        });

        log.info("Transaction cleaner job terminated");
    }

    @Override
    public boolean requireTenant() {
        return false;
    }

    private void filter(final Connection conn, final String schemaName, final String sql, final Map<Long,ApplicationTransaction> txMap, final Set<Long> usedTxSet) throws SQLException {
        if (!txMap.isEmpty()) {
            try (var stmt = conn.prepareStatement(String.format(sql, schemaName))) {
                stmt.setArray(1, conn.createArrayOf("INTEGER", txMap.keySet().toArray(new Long[0])));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long tx = rs.getLong("tx");
                        usedTxSet.add(tx);
                        txMap.remove(tx);
                    }
                }
            }
        }
    }
}

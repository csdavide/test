package it.doqui.libra.librabl.business.provider.data.dao;

import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.*;

@ApplicationScoped
@Slf4j
public class TxDAO extends AbstractDAO {

    public long count(ApplicationTransaction tx) {
        return DBUtils.call(ds, tx.getDbSchema(), conn -> {
            var sql = """
                select sum(x) from (\s
                select count(*) x from ecm_nodes where tx = ?\s
                union\s
                select count(*) x from ecm_archived_nodes where tx = ?\s
                ) t
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, tx.getId());
                stmt.setLong(2, tx.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var n = rs.getLong(1);
                        log.debug("TX {} includes {} nodes", tx.getId(), n);
                        return n;
                    }
                }
            } catch (SQLException e) {
                log.error("Unable to count nodes in transaction {}: {}", tx.getId(), e.getMessage());
            }

            return 0L;
        });
    }

    public ApplicationTransaction createTransaction() {
        return call(conn -> {
            var sql = """
                insert into ecm_transactions (tenant,uuid,created_at)\s
                values (?,?,?)
                """;
            try (var stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                var tx = new ApplicationTransaction();
                tx.setTenant(UserContextManager.getTenant());
                tx.setUuid(UUID.randomUUID().toString());
                tx.setCreatedAt(ZonedDateTime.now());
                tx.setDbSchema(UserContextManager.getContext().getDbSchema());

                stmt.setString(1, tx.getTenant());
                stmt.setString(2, tx.getUuid());
                stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));

                if (stmt.executeUpdate() < 1) {
                    throw new SystemException("Unable to create a new transaction");
                }

                try (var generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        tx.setId(generatedKeys.getLong(1));
                    } else {
                        throw new SystemException("Transaction creation failed, no ID obtained.");
                    }
                }

                return tx;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<ApplicationTransaction> findTransactionOfNode(String uuid) {
        return call(conn -> {
            final var sql = """
                select t.id,t.tenant,t.uuid,t.created_at,t.indexed_at\s
                from ecm_transactions t\s
                join ecm_nodes n on (n.tx = t.id)\s
                where n.tenant = ? and n.uuid = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setString(2, uuid);
                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(readTx(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public List<ApplicationTransaction> listTransactions(Collection<String> uuids) {
        return call(conn -> {
            var sql = """
                select t.id,t.tenant,t.uuid,t.created_at,t.indexed_at\s
                from ecm_transactions t
                where t.tenant = ? and t.uuid = any(?)
                """;

            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setArray(2, conn.createArrayOf("VARCHAR", uuids.toArray(new String[0])));
                try (var rs = stmt.executeQuery()) {
                    var result = new LinkedList<ApplicationTransaction>();
                    while (rs.next()) {
                        result.add(readTx(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<ApplicationTransaction> findTransactionById(long id) {
        return findTransactionByObject(id);
    }

    private Optional<ApplicationTransaction> findTransactionByObject(Object id) {
        return call(conn -> {
            var sql = """
                select t.id,t.tenant,t.uuid,t.created_at,t.indexed_at\s
                from ecm_transactions t
                """;

            if (id instanceof Number) {
                sql += " where t.id = ?";
            } else if (id instanceof String) {
                sql += " where t.tenant = ? and t.uuid = ?";
            } else {
                throw new IllegalArgumentException("Invalid id format");
            }

            try (var stmt = conn.prepareStatement(sql)) {
                int c = 0;
                if (id instanceof Number num) {
                    stmt.setLong(++c, num.longValue());
                } else {
                    stmt.setString(++c, UserContextManager.getTenant());
                    stmt.setString(++c, id.toString());
                }

                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(readTx(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }
}

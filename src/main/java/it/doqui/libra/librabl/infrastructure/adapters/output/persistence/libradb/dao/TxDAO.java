package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class TxDAO extends AbstractDAO {

    public long count(ApplicationTransaction tx, long limit) {
        return DBUtils.call(ds, tx.getDbSchema(), conn -> {
            var counter = 0L;
            for (var reindexTable : ReindexTable.values()) {
                var sql = "select count(*) x from " + reindexTable.mapToTableName(true);
                if (reindexTable.equals(ReindexTable.TX_NODES)) {
                    sql += " join ecm_nodes n on (tn.node_id = n.id and tn.tx_id > n.tx) where tn.tx_id = ?";
                } else if (reindexTable.equals(ReindexTable.PATHS)) {
                    sql += " join ecm_nodes n on (p.node_id = n.id and p.tx > n.tx) where p.tx = ?";
                } else {
                    sql += " where tx = ?";
                }
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, tx.getId());
                    try (var rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            var n = rs.getLong(1);
                            log.debug("TX {} includes {} nodes from {}", tx.getId(), n, reindexTable.mapToTableName(false));
                            counter += n;
                            if (counter >= limit) {
                                return counter;
                            }
                        }
                    }
                } catch (SQLException e) {
                    log.error("Unable to count nodes in transaction {}: {}", tx.getId(), e.getMessage());
                }
            }

            return counter;
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
                tx.setTenant(sessionContext.getTenant());
                tx.setUuid(UuidCreator.getTimeOrderedEpoch().toString());
                tx.setCreatedAt(ZonedDateTime.now());
                tx.setDbSchema(sessionContext.getUserContext().getDbSchema());

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

    public Optional<Long> findTransactionOfNode(String uuid) {
        return call(conn -> {
            final var sql = """
                select n.tx\s
                from ecm_nodes n\s
                where n.tenant = ? and n.uuid = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sessionContext.getTenant());
                stmt.setString(2, uuid);
                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getLong("tx")) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }
}

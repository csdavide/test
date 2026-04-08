package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.deleters;

import it.doqui.libra.librabl.domain.model.tenant.TenantLimit;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.AbstractDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.Association;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

@Slf4j
public abstract class AbstractDeleterDAO extends AbstractDAO {

    @ConfigProperty(name = "libra.delete.deferred", defaultValue = "false")
    boolean deleteDeferred;

    protected void deletePaths(Connection conn, Association association) throws SQLException {
        log.debug("Deleting paths...");
        if (association.getHard() != null) {
            if (association.getParent() != null) {
                var parts = new Long[2];
                parts[0] = association.getParent().getId();
                parts[1] = association.getChild().getId();

                try (var stmt = conn.prepareStatement("""
                    delete from ecm_paths where path_parts @> ?\s
                    and array_position(path_parts, ?::integer) = array_position(path_parts, ?::integer) - 1
                    """))
                {
                    stmt.setArray(1, conn.createArrayOf("INTEGER", parts));
                    stmt.setLong(2, parts[0]);
                    stmt.setLong(3, parts[1]);
                    int n = stmt.executeUpdate();
                    log.debug("{} paths removed", n);
                }
            } else {
                // support for null association
                var parts = new Long[1];
                parts[0] = association.getChild().getId();

                try (var stmt = conn.prepareStatement("delete from ecm_paths where path_parts @> ?"))
                {
                    stmt.setArray(1, conn.createArrayOf("INTEGER", parts));
                    int n = stmt.executeUpdate();
                    log.debug("{} paths removed", n);
                }
            }
        }
    }

    protected void deleteMainAssociation(Connection conn, long associationId, boolean archive, DeleteMode deleteMode) throws SQLException {
        if (archive && deleteMode == DeleteMode.DELETE) {
            log.debug("Inserting main association into archived...");
            try (var stmt = conn.prepareStatement("""
                    insert into ecm_archived_associations (id, parent_id, child_id, type_name, name, code, is_hard)\s
                    select a.id, a.parent_id, a.child_id, a.type_name, a.name, a.code, a.is_hard\s
                    from ecm_associations a\s
                    where a.id = ?
                    """))
            {
                stmt.setLong(1, associationId);
                if (stmt.executeUpdate() > 0) {
                    log.debug("Association {} copied into archive", associationId);
                } else {
                    log.warn("Association {} not copied into archive", associationId);
                }
            } catch (SQLException e) {
                log.error("Cannot copy main archived association {}: {}", associationId, e.getMessage());
                throw e;
            }
        }

        log.debug("Deleting main association...");
        try (var stmt = conn.prepareStatement("delete from ecm_associations where id = ?")) {
            stmt.setLong(1, associationId);
            stmt.executeUpdate();
            log.debug("Association {} deleted", associationId);
        } catch (SQLException e) {
            log.error("Cannot delete main association {}: {}", associationId, e.getMessage());
            throw e;
        }
    }

    protected void updateMainNodeWithArchivedAspect(Connection conn, long nodeId) throws SQLException {
        log.debug("Updating main node with archived aspect...");
        try (var stmt = conn.prepareStatement("""
                update ecm_nodes\s
                set data = jsonb_set(data, '{aspects}', data->'aspects' || '"sys:archived"')\s
                where id = ?
                """)) {
            stmt.setLong(1, nodeId);
            int n = stmt.executeUpdate();
            log.debug("{} aspect 'sys:archived' added", n);
        } catch (SQLException e) {
            log.error("Cannot insert aspect 'sys:archived'");
            throw e;
        }
    }

    protected Long[] listArchivedNodes(Connection conn, long txId) throws SQLException {
        try (var stmt = conn.prepareStatement("select id from ecm_archived_nodes where tx = ?")) {
            return listNodes(stmt, "ecm_archived_nodes", txId);
        }
    }

    protected Long[] listPurgedNodes(Connection conn, long txId) throws SQLException {
        try (var stmt = conn.prepareStatement("select id from ecm_removed_nodes where tx = ?")) {
            return listNodes(stmt, "ecm_removed_nodes", txId);
        }
    }

    private Long[] listNodes(PreparedStatement stmt, String tableName, long txId) throws SQLException {
        stmt.setLong(1, txId);
        try (var rs = stmt.executeQuery()) {
            var nodeIds = new ArrayList<Long>();
            while (rs.next()) {
                nodeIds.add(rs.getLong(tableName.equals("ecm_transaction_nodes") ? "node_id" : "id"));
            }
            log.debug("{} {} nodes retrieved", nodeIds.size(), typeOfNodes(tableName));
            return nodeIds.toArray(new Long[0]);
        }
    }

    private String typeOfNodes(String tableName) {
        return switch (tableName) {
            case "ecm_transaction_nodes" -> "tn";
            case "ecm_nodes" -> "active";
            case "ecm_archived_nodes" -> "archived";
            case "ecm_removed_nodes" -> "removed";
            default -> null;
        };
    }

    protected void cleanSecurityGroups(Connection conn, long txId) throws SQLException {
        try (var stmt = conn.prepareStatement("""
            update ecm_security_groups set tx = ?, updated_at = ?\s
            where managed and id in (select sg_id from ecm_removed_nodes where tx = ?)
            """)) {
            stmt.setLong(1, txId);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setLong(3, txId);
            int n = stmt.executeUpdate();
            log.debug("{} SGs of removed nodes updated", n);
        } catch (SQLException e) {
            log.error("Cannot updated security groups");
            throw e;
        }

        try (var stmt = conn.prepareStatement("""
                delete from ecm_access_rules\s
                where sg_id in (select id from ecm_security_groups where managed and tx = ?)
                """))
        {
            stmt.setLong(1, txId);
            int n = stmt.executeUpdate();
            log.debug("{} access rules of SG deleted", n);
        } catch (SQLException e) {
            log.error("Cannot delete access rules");
            throw e;
        }
    }

    protected int limit(boolean insertModeEnabled) {
        return configurationRepository.getLimit(
            TenantLimit.Operation.DELETE,
            sessionContext.getMode(),
            insertModeEnabled ? TenantLimit.LimitFeature.HIGHER : TenantLimit.LimitFeature.DEFAULT
        );
    }
}

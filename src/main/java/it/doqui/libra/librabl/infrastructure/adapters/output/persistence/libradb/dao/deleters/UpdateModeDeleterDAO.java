package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.deleters;

import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.Association;

import it.doqui.libra.librabl.foundation.exceptions.LimitExceededException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.TransactionalNodesItem;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
@Slf4j
public class UpdateModeDeleterDAO extends AbstractDeleterDAO implements AssociationDeleterDAO {

    @Override
    public void selectCandidateNodes(Connection conn, Association association, AtomicLong counter, long txId) throws SQLException {
        log.debug("Updating tx of candidate nodes...");
        if (association.getHard() != null) {
            if (association.getParent() != null) {
                var parts = new Long[2];
                parts[0] = association.getParent().getId();
                parts[1] = association.getChild().getId();

                try (var stmt = conn.prepareStatement("""
                        update ecm_nodes set tx = ?\s
                        where id in\s
                        (select node_id from ecm_paths where path_parts @> ?\s
                        and array_position(path_parts, ?::integer) = array_position(path_parts, ?::integer) - 1)
                        """)) {
                    stmt.setLong(1, txId);
                    stmt.setArray(2, conn.createArrayOf("INTEGER", parts));
                    stmt.setLong(3, parts[0]);
                    stmt.setLong(4, parts[1]);
                    int n = stmt.executeUpdate();
                    log.debug("{} nodes updated with tx {}", n, txId);
                    if (counter != null) {
                        counter.addAndGet(n);
                    }
                }
            } else {
                // support for null association
                var parts = new Long[1];
                parts[0] = association.getChild().getId();

                try (var stmt = conn.prepareStatement("""
                        update ecm_nodes set tx = ?\s
                        where id = ? or id in (select node_id from ecm_paths where path_parts @> ?)
                        """)) {
                    stmt.setLong(1, txId);
                    stmt.setLong(2, association.getChild().getId());
                    stmt.setArray(3, conn.createArrayOf("INTEGER", parts));
                    int n = stmt.executeUpdate();
                    log.debug("{} nodes updated with tx {}", n, txId);
                    if (counter != null) {
                        counter.addAndGet(n);
                    }
                }
            }
        } else {
            //support for light associations
            try (var stmt = conn.prepareStatement("update ecm_nodes set tx = ? where id = ?")) {
                stmt.setLong(1, txId);
                stmt.setLong(2, association.getChild().getId());
                int n = stmt.executeUpdate();
                log.debug("{} nodes updated with tx {}", n, txId);
                if (counter != null) {
                    counter.addAndGet(n);
                }
            }
        }
    }

    @Override
    public void deletePaths(Connection conn, Association association) throws SQLException {
        super.deletePaths(conn, association);
    }

    @Override
    public void deleteMainAssociation(Connection conn, long associationId, boolean archive, DeleteMode deleteMode) throws SQLException {
        super.deleteMainAssociation(conn, associationId, archive, deleteMode);
    }

    @Override
    public Long[] insertArchivedNodes(Connection conn, Association association, long txId, boolean insertModeEnabled) throws SQLException {
        updateMainNodeWithArchivedAspect(conn, association.getChild().getId());
        int limit = limit(insertModeEnabled);

        log.debug("Inserting archived nodes...");
        try (var stmt = conn.prepareStatement("""
                insert into ecm_archived_nodes (id, tenant, uuid, version, type_name, sg_id, tx, updated_at, data)\s
                select n.id, n.tenant, n.uuid, n.version, n.type_name, n.sg_id, n.tx, n.updated_at,
                    jsonb_set(\s
                        jsonb_set(\s
                            n.data,
                            '{"properties", "sys:archivedBy"}',
                            to_jsonb(?)
                        ),
                        '{"properties", "sys:archivedDate"}',
                        to_jsonb(now())\s
                    )\s
                from ecm_nodes n\s
                left outer join ecm_paths p on (p.node_id = n.id and coalesce(p.is_hard,false))\s
                where n.tx = ? and p.id is null
                """)) {
            stmt.setString(1, sessionContext.getUserContext().getAuthorityRef().toString());
            stmt.setLong(2, txId);
            int n = stmt.executeUpdate();
            if (n > limit) {
                throw new LimitExceededException("Delete limit exceeded: " + limit);
            }
            log.debug("{} archived nodes inserted", n);
        } catch (SQLException e) {
            log.error("Cannot insert archived nodes");
            throw e;
        }

        return listArchivedNodes(conn, txId);
    }

    @Override
    public Long[] insertRemovedNodes(Connection conn, DeleteMode deleteMode, long txId, boolean insertModeEnabled) throws SQLException {
        int limit = limit(insertModeEnabled);

        log.debug("Inserting removed nodes...");
        try (var stmt = conn.prepareStatement("""
                insert into ecm_removed_nodes (id, tenant, uuid, data, tx, sg_id, wipeable)\s
                select n.id, n.tenant, n.uuid,
                case when jsonb_exists(n.data, 'contents') then jsonb_build_object('contents', n.data->'contents') end,
                    n.tx, n.sg_id, ?\s
                from ecm_nodes n left outer join ecm_paths p on (p.node_id = n.id and coalesce(p.is_hard,false))\s
                where n.tx = ? and p.id is null
                """))
        {
            stmt.setBoolean(1, deleteMode == DeleteMode.PURGE_COMPLETE || deleteMode == DeleteMode.EXPIRED);
            stmt.setLong(2, txId);

            int n = stmt.executeUpdate();
            if (n > limit) {
                throw new LimitExceededException("Delete limit exceeded: " + limit);
            }
            log.debug("{} removed nodes inserted", n);
        } catch (SQLException e) {
            log.error("Cannot insert removed nodes");
            throw e;
        }

        return listPurgedNodes(conn, txId);
    }

    @Override
    public void insertArchivedAssociations(Connection conn, TransactionalNodesItem txNodesItem) throws SQLException {
        checkTxNodesItem(txNodesItem);

        log.debug("Inserting archived associations...");
        try (var stmt = conn.prepareStatement("""
                insert into ecm_archived_associations (id,parent_id,child_id,type_name,name,code,is_hard)\s
                select a.id, a.parent_id, a.child_id, a.type_name, a.name, a.code, a.is_hard\s
                from ecm_associations a\s
                where a.child_id = any(?) or a.parent_id = any(?)
                """))
        {
            stmt.setArray(1, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
            stmt.setArray(2, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
            int n = stmt.executeUpdate();
            log.debug("{} archived associations inserted", n);
        } catch (SQLException e) {
            log.error("Cannot insert archived associations");
            throw e;
        }
    }

    @Override
    public void deleteTransactionNodes(Connection conn, DeleteMode deleteMode, long txId) {
        //do nothing
    }

    @Override
    public void deleteSoftRemainingPaths(Connection conn, DeleteMode deleteMode, TransactionalNodesItem txNodesItem) throws SQLException {
        checkTxNodesItem(txNodesItem);

        log.debug("Deleting soft remaining paths...");
        try (var stmt = conn.prepareStatement("delete from ecm_paths where path_parts && ?"))
        {
            stmt.setArray(1, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
            int n = stmt.executeUpdate();
            log.debug("{} soft remaining paths deleted", n);
        } catch (SQLException e) {
            log.error("Cannot delete remaining paths");
            throw e;
        }
    }

    @Override
    public void deleteAssociations(Connection conn, DeleteMode deleteMode, TransactionalNodesItem txNodesItem) throws SQLException {
        checkTxNodesItem(txNodesItem);

        log.debug("Deleting associations...");
        try (var stmt = conn.prepareStatement("""
                delete from ecm_associations\s
                where child_id = any(?)\s
                or parent_id = any(?)
                """))
        {
            stmt.setArray(1, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
            stmt.setArray(2, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
            int n = stmt.executeUpdate();
            log.debug("{} associations deleted", n);
        } catch (SQLException e) {
            log.error("Cannot delete associations");
            throw e;
        }
    }

    @Override
    public void deleteNodes(Connection conn, DeleteMode deleteMode, TransactionalNodesItem txNodesItem) throws SQLException {
        checkTxNodesItem(txNodesItem);

        if (deleteDeferred) {
            log.debug("Adding deleted aspect...");
            try (var stmt = conn.prepareStatement("""
                    update ecm_nodes\s
                    set data = jsonb_set(data, '{aspects}', data->'aspects' || '"ecm-sys:deleted"')\s
                    where id = any(?)
                    """))
            {
                stmt.setArray(1, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
                int n = stmt.executeUpdate();
                log.debug("{} aspect 'ecm-sys:deleted' added", n);
            } catch (SQLException e) {
                log.error("Cannot insert aspect 'ecm-sys:deleted'");
                throw e;
            }
        } else {
            log.debug("Deleting nodes...");
            try (var stmt = conn.prepareStatement("delete from ecm_nodes where id = any(?)"))
            {
                stmt.setArray(1, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
                int n = stmt.executeUpdate();
                log.debug("{} nodes deleted", n);
            } catch (SQLException e) {
                log.error("Cannot delete nodes");
                throw e;
            }
        }
    }

    @Override
    public void cleanSecurityGroups(Connection conn, long txId) throws SQLException {
        super.cleanSecurityGroups(conn, txId);
    }

    @Override
    public void purgeNodeAccessories(Connection conn, TransactionalNodesItem txNodesItem) throws SQLException {
        checkTxNodesItem(txNodesItem);

        try (var stmt = conn.prepareStatement("delete from ecm_versions where node_id = any (?)")) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
            stmt.executeUpdate();
        }

        try (var stmt = conn.prepareStatement("delete from ecm_external_properties where node_id = any (?)")) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", txNodesItem.getNodesArray()));
            stmt.executeUpdate();
        }
    }

    private void checkTxNodesItem(TransactionalNodesItem txNodesItem) {
        if (txNodesItem.getNodesArray() == null) {
            throw new SystemException("No nodes array exists");
        }
    }
}

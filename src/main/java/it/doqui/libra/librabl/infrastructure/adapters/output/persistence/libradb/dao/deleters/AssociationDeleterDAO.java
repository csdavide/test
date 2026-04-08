package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.deleters;

import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.Association;
import it.doqui.libra.librabl.domain.policy.DeleteMode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.TransactionalNodesItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public interface AssociationDeleterDAO {
    void selectCandidateNodes(Connection conn, Association association, AtomicLong counter, long txId) throws SQLException;
    void deletePaths(Connection conn, Association association) throws SQLException;
    void deleteMainAssociation(Connection conn, long associationId, boolean archive, DeleteMode deleteMode) throws SQLException;
    Long[] insertArchivedNodes(Connection conn, Association association, long txId, boolean insertModeEnabled) throws SQLException;
    Long[] insertRemovedNodes(Connection conn, DeleteMode deleteMode, long txId, boolean insertModeEnabled) throws SQLException;
    void insertArchivedAssociations(Connection conn, TransactionalNodesItem txNodesItem) throws SQLException;
    void deleteTransactionNodes(Connection conn, DeleteMode deleteMode, long txId) throws SQLException;
    void deleteSoftRemainingPaths(Connection conn, DeleteMode deleteMode, TransactionalNodesItem txNodesItem) throws SQLException;
    void deleteAssociations(Connection conn, DeleteMode deleteMode, TransactionalNodesItem txNodesItem) throws SQLException;
    void deleteNodes(Connection conn, DeleteMode deleteMode, TransactionalNodesItem txNodesItem) throws SQLException;
    void cleanSecurityGroups(Connection conn, long txId) throws SQLException;
    void purgeNodeAccessories(Connection conn, TransactionalNodesItem txNodesItem) throws SQLException;
}

package it.doqui.libra.librabl.application.service;


import it.doqui.libra.librabl.domain.model.graph.Constants;
import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.model.graph.IndexingFlags;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.session.PerformResult;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.NodeLoaderDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.QueryContext;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.VersionDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.Set;

@ApplicationScoped
@Slf4j
public class SimpleNodeAccessManager {

    @Inject
    NodeDAO nodeDAO;

    @Inject
    NodeLoaderDAO nodeLoaderDAO;

    @Inject
    PermissionValidator permissionValidator;

    @Inject
    VersionDAO versionDAO;
    
    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    public ActiveNode getNode(String uuid, PermissionFlag requiredPermission) {
        return getNode(uuid, Set.of(MapOption.DEFAULT), requiredPermission);
    }

    public ActiveNode getNode(String uuid, Set<MapOption> optionSet, PermissionFlag requiredPermission) {
        return getNode(uuid, optionSet, requiredPermission, requiredPermission == PermissionFlag.W ? QueryScope.UPDATE : QueryScope.DEFAULT);
    }

    public ActiveNode getNode(String uuid, Set<MapOption> optionSet, PermissionFlag requiredPermission, QueryScope scope) {
        return getNode(new Vertex(VertexType.UUID, uuid), optionSet, requiredPermission, scope);
    }

    public ActiveNode getNode(Vertex vertex, PermissionFlag requiredPermission) {
        return getNode(vertex, Set.of(MapOption.DEFAULT), requiredPermission);
    }

    public ActiveNode getNode(Vertex vertex, Set<MapOption> optionSet, PermissionFlag requiredPermission) {
        return getNode(vertex, optionSet, requiredPermission, requiredPermission == PermissionFlag.W ? QueryScope.UPDATE : QueryScope.DEFAULT);
    }

    public ActiveNode getNode(Vertex vertex, Set<MapOption> optionSet, PermissionFlag requiredPermission, QueryScope scope) {
        var qc = QueryContext.builder()
                .schema(sessionContext.getUserContext().getDbSchema())
                .tenant(sessionContext.getTenant())
                .optionSet(optionSet)
                .scope(scope)
                .build();
        return nodeLoaderDAO
                .getNode(vertex, qc)
                .map(node -> permissionValidator.requirePermission(node, requiredPermission))
                .orElseThrow(() -> new NotFoundException(vertex.toString()));
    }

    public void updateNode(ApplicationTransaction tx, ActiveNode node) {
        updateNode(tx, node, IndexingFlags.FULL_FLAG_MASK, false);
    }

    public void updateNode(ApplicationTransaction tx, ActiveNode node, int txFlag, boolean requiresVersioning) {
        node.setTx(tx);
        node.setTransactionFlags(IndexingFlags.formatAsBinary(txFlag));
        node.setUpdatedAt(ZonedDateTime.now());
        updateNodeWithVersioning(node, requiresVersioning);
    }

    public void updateNode(ActiveNode node) {
        updateNodeWithVersioning(node, false);
    }

    public void updateNodeWithVersioning(ActiveNode node, boolean requiresVersioning) {
        node.getData().getLocks()
            .stream()
            .filter(l -> l.getExpires() != null && l.getExpires().isBefore(ZonedDateTime.now()))
            .toList()
            .forEach(l -> node.getData().getLocks().remove(l));

        nodeDAO.updateNode(node);

        if (requiresVersioning || ObjectUtils.getAsBoolean(node.getProperties().get(Constants.CM_AUTO_VERSION), false)) {
            versionDAO.createNodeVersion(node, null);
        }
    }

    public void updateNodeWithIndexing(ActiveNode node, boolean indexingRequired) {
        if (indexingRequired) {
            transactionManagerPort.perform(tx -> {
                updateNode(tx, node);
                return PerformResult.<Void>builder()
                    .mode(PerformResult.Mode.SYNC)
                    .count(1)
                    .build();
            });
        } else {
            updateNode(node);
        }
    }

}

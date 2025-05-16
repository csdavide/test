package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.VersionDAO;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.node.MapOption;
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
    PermissionValidator permissionValidator;

    @Inject
    VersionDAO versionDAO;

    public ActiveNode getNode(String uuid, PermissionFlag requiredPermission) {
        return getNode(uuid, Set.of(MapOption.DEFAULT), requiredPermission);
    }

    public ActiveNode getNode(String uuid, Set<MapOption> optionSet, PermissionFlag requiredPermission) {
        return nodeDAO
            .findNodeByUUID(uuid, optionSet, QueryScope.DEFAULT)
            .map(node -> permissionValidator.requirePermission(node, requiredPermission))
            .orElseThrow(() -> new NotFoundException(uuid));
    }

    public void updateNode(ApplicationTransaction tx, ActiveNode node) {
        updateNode(tx, node, IndexingFlags.FULL_FLAG_MASK, false);
    }

    public void updateNode(ApplicationTransaction tx, ActiveNode node, int txFlag, boolean requiresVersioning) {
        node.setTx(tx);
        node.setTransactionFlags(IndexingFlags.formatAsBinary(txFlag));
        node.setUpdatedAt(ZonedDateTime.now());
        nodeDAO.updateNode(node);

        if (requiresVersioning || ObjectUtils.getAsBoolean(node.getProperties().get(Constants.CM_AUTO_VERSION), false)) {
            versionDAO.createNodeVersion(node, null);
        }
    }

}

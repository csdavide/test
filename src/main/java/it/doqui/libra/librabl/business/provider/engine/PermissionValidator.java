package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.dao.AclDAO;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.ArchivedNode;
import it.doqui.libra.librabl.business.provider.data.entities.GraphNode;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;

@ApplicationScoped
@Slf4j
class PermissionValidator {

    @Inject
    AclDAO aclDAO;

    private boolean isOwner(GraphNode node, final UserContext ctx) {
        if (ctx.isAdmin()) {
            return true;
        }

        final Map<String, Object> properties = node.getData().getProperties();
        Optional<Object> owner = Optional.ofNullable(properties.get(CM_OWNER));
        if (owner.isEmpty()) {
            owner = Optional.ofNullable(properties.get(CM_CREATOR));
        }

        return owner
            .map(Object::toString)
            .filter(x -> StringUtils.equalsIgnoreCase(x, ctx.getAuthority()))
            .isPresent();
    }

    private boolean isCreator(GraphNode node, final UserContext ctx) {
        if (ctx.isAdmin()) {
            return true;
        }

        final Map<String, Object> properties = node.getData().getProperties();
        Optional<Object> creator = Optional.ofNullable(properties.get(CM_CREATOR));
        return creator
            .map(Object::toString)
            .filter(x -> StringUtils.equalsIgnoreCase(x, ctx.getAuthority()))
            .isPresent();
    }

    private boolean isNotAllowed(GraphNode node, PermissionFlag p) {
        if (p == null) {
            return false;
        }

        final UserContext ctx = UserContextManager.getContext();
        if (isOwner(node, ctx)) {
            return false;
        }

        if (node instanceof ActiveNode) {
            return aclDAO
                .listPermissions((ActiveNode) node, true, true)
                .stream()
                .noneMatch(r -> p.match(r.getRights()));
        } else if (node instanceof ArchivedNode) {
            // Quando si cancella un contenuto, si registra l'utente che effettua la cancellazione
            // e utilizzarlo per permettere il purge diretto dal cestino, oppure in casi di restore

            final Map<String, Object> properties = node.getData().getProperties();
            Optional<Object> modifier = Optional.ofNullable(properties.get(PROP_SYS_ARCHIVEDBY));
            return modifier
                .map(Object::toString)
                .filter(x -> StringUtils.equalsIgnoreCase(x, ctx.getAuthority()))
                .isEmpty();
        }

        return true;
    }

    protected <T> T requirePermission(GraphNode node, PermissionFlag p, T result) {
        if (isNotAllowed(node, p)) {
            throw new ForbiddenException("Permission " + p.name() + " is required on node " + node.getUuid());
        }
        return result;
    }

    protected ActiveNode requirePermission(ActiveNode node, PermissionFlag p) {
        if (isNotAllowed(node, p)) {
            throw new ForbiddenException("Permission " + p.name() + " is required on node " + node.getUuid());
        }

        return node;
    }

    protected ActiveNode requireOwnership(ActiveNode node) {
        if (!isOwner(node, UserContextManager.getContext())) {
            throw new ForbiddenException("Ownership required on node " + node.getUuid());
        }

        return node;
    }

    protected ActiveNode requireCreator(ActiveNode node) {
        if (!isCreator(node, UserContextManager.getContext())) {
            throw new ForbiddenException("Creator required on node " + node.getUuid());
        }

        return node;
    }

    protected int permissions(ActiveNode node) {
        final UserContext ctx = UserContextManager.getContext();
        if (isOwner(node, ctx)) {
            return PermissionFlag.all();
        }

        return aclDAO
            .listPermissions(node, true, true)
            .stream()
            .map(r -> PermissionFlag.parse(r.getRights()))
            .reduce(0, (a, b) -> a | b);
    }

    protected void requirePermission(ActiveNode node, int rights, PermissionFlag p) {
        if (!p.match(rights)) {
            throw new ForbiddenException("Permission " + p.name() + " is required on node " + node.getUuid());
        }
    }

}

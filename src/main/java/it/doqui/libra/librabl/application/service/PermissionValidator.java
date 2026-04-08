package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.domain.model.acl.PermissionFlag;
import it.doqui.libra.librabl.domain.model.graph.GraphNode;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao.AclDAO;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ArchivedNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

import java.util.Map;
import java.util.Optional;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;

@ApplicationScoped
@Slf4j
class PermissionValidator {

    @Inject
    AclDAO aclDAO;

    @Inject
    SessionContext sessionContext;

    private boolean isOwner(GraphNode node, final UserContext ctx) {
        if (ctx.isAdmin()) {
            return true;
        }

        final Map<String, Object> properties = node.getProperties();
        Optional<Object> owner = Optional.ofNullable(properties.get(CM_OWNER));
        if (owner.isEmpty()) {
            owner = Optional.ofNullable(properties.get(CM_CREATOR));
        }

        return owner
            .map(Object::toString)
            .filter(x -> Strings.CI.equals(x, ctx.getAuthority()))
            .isPresent();
    }

    private boolean isCreator(GraphNode node, final UserContext ctx) {
        if (ctx.isAdmin()) {
            return true;
        }

        final Map<String, Object> properties = node.getProperties();
        Optional<Object> creator = Optional.ofNullable(properties.get(CM_CREATOR));
        return creator
            .map(Object::toString)
            .filter(x -> Strings.CI.equals(x, ctx.getAuthority()))
            .isPresent();
    }

    private boolean isNotAllowed(GraphNode node, PermissionFlag p) {
        if (p == null) {
            return false;
        }

        final UserContext ctx = sessionContext.getUserContext();
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

            final Map<String, Object> properties = node.getProperties();
            Optional<Object> modifier = Optional.ofNullable(properties.get(PROP_SYS_ARCHIVEDBY));
            return modifier
                .map(Object::toString)
                .filter(x -> Strings.CI.equals(x, ctx.getAuthority()))
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

    protected <T extends GraphNode> T requirePermission(T node, PermissionFlag p) {
        if (isNotAllowed(node, p)) {
            throw new ForbiddenException("Permission " + p.name() + " is required on node " + node.getUuid());
        }

        return node;
    }

    protected boolean requirePermission(GraphNode node, PermissionFlag p, boolean throwException) {
        boolean allowed = !isNotAllowed(node, p);
        if (throwException && !allowed) {
            if (node instanceof ActiveNode) {
                throw new ForbiddenException("Permission " + p.name() + " is required on node " + node.getUuid());
            } else {
                throw new ForbiddenException("Permission denied");
            }

        }
        return allowed;
    }

    protected ActiveNode requireOwnership(ActiveNode node) {
        if (!isOwner(node, sessionContext.getUserContext())) {
            throw new ForbiddenException("Ownership required on node " + node.getUuid());
        }

        return node;
    }

    protected ActiveNode requireCreator(ActiveNode node) {
        if (!isCreator(node, sessionContext.getUserContext())) {
            throw new ForbiddenException("Creator required on node " + node.getUuid());
        }

        return node;
    }

    protected int permissions(ActiveNode node) {
        final UserContext ctx = sessionContext.getUserContext();
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

package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.provider.data.dao.AclDAO;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.PathDAO;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.provider.data.entities.SecurityGroup;
import it.doqui.libra.librabl.business.provider.security.AuthorityUtils;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.PermissionService;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.views.acl.PermissionItem;
import it.doqui.libra.librabl.views.acl.PermissionsDescriptor;
import it.doqui.libra.librabl.views.node.MapOption;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    @Inject
    TransactionService txManager;

    @Inject
    AclDAO aclDAO;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    PathDAO pathDAO;

    @Inject
    PermissionValidator permissionValidator;

    @Override
    public void addPermissions(String uuid, Collection<PermissionItem> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        txManager.perform(tx -> {
            var node = findManagedNode(uuid);
            permissionValidator.requirePermission(node, PermissionFlag.A);
            aclDAO.addAccessRules(node.getSecurityGroup().getId(), TenantRef.valueOf(node.getTenant()), permissions);
            node.getSecurityGroup().setTx(tx); //TODO: set tx solo se vi è una variazione su R
            aclDAO.setTx(node.getSecurityGroup().getId(), tx.getId());

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    public void replacePermissions(String uuid, Collection<PermissionItem> permissions, Optional<Boolean> inheritance) {
        txManager.perform(tx -> {
            var node = findManagedNode(uuid);
            permissionValidator.requirePermission(node, PermissionFlag.A);
            var sgId = node.getSecurityGroup().getId();
            aclDAO.removeAllSecurityGroupRules(sgId);
            aclDAO.addAccessRules(sgId, TenantRef.valueOf(node.getTenant()), permissions);

            var inheritanceChanged = false;
            if (inheritance.isPresent()) {
                if (inheritance.get() != node.getSecurityGroup().isInheritanceEnabled()) {
                    inheritanceChanged = true;
                    node.getSecurityGroup().setInheritanceEnabled(inheritance.get());
                }
            }
            node.getSecurityGroup().setTx(tx);

            if (inheritanceChanged) {
                aclDAO.updateInheritance(node.getSecurityGroup().getId(), inheritance.get(), tx.getId());
                alterInheritanceOnPaths(tx, node);
            } else {
                aclDAO.setTx(node.getSecurityGroup().getId(), tx.getId());
            }

            return PerformResult.<Void>builder()
                .mode(inheritanceChanged ? PerformResult.Mode.ASYNC : PerformResult.Mode.SYNC)
                .count(inheritanceChanged ? 0 : 1)
                .build();
        });
    }

    @Override
    public void setInheritance(String uuid, boolean inheritance) {
        txManager.perform(tx -> {
            var node = findManagedNode(uuid);
            permissionValidator.requirePermission(node, PermissionFlag.A);
            var inheritanceChanged = false;
            if (inheritance != node.getSecurityGroup().isInheritanceEnabled()) {
                inheritanceChanged = true;
                node.getSecurityGroup().setInheritanceEnabled(inheritance);
                node.getSecurityGroup().setTx(tx);
                aclDAO.updateInheritance(node.getSecurityGroup().getId(), inheritance, tx.getId());
                alterInheritanceOnPaths(tx, node);
            }

            return PerformResult.<Void>builder()
                .mode(inheritanceChanged ? PerformResult.Mode.ASYNC : PerformResult.Mode.NONE)
                .build();
        });
    }

    @Override
    public void removeAllAuthorityPermissions(String uuid, String authority) {
        txManager.perform(tx -> {
            var node = findManagedNode(uuid);
            permissionValidator.requirePermission(node, PermissionFlag.A);
            var a = AuthorityUtils.normalizeAuthority(authority, UserContextManager.getContext().getTenantRef());
            aclDAO.deleteAuthorityFromSG(node.getSecurityGroup().getId(), a);
            node.getSecurityGroup().setTx(tx); //TODO: set tx solo se vi è una variazione su R o inheritanceChanged
            aclDAO.setTx(node.getSecurityGroup().getId(), tx.getId());

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    public void removePermissions(String uuid, Collection<PermissionItem> permissions) {
        txManager.perform(tx -> {
            var node = findManagedNode(uuid);
            permissionValidator.requirePermission(node, PermissionFlag.A);
            aclDAO.removePermissions(node.getSecurityGroup().getId(), permissions);
            node.getSecurityGroup().setTx(tx); //TODO: set tx solo se vi è una variazione su R o inheritanceChanged
            aclDAO.setTx(node.getSecurityGroup().getId(), tx.getId());

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .build();
        });
    }

    @Override
    public boolean isInheritance(String uuid) {
        var node = findNode(uuid);
        permissionValidator.requirePermission(node, PermissionFlag.R);
        return node.getSecurityGroup().isInheritanceEnabled();
    }

    @Override
    public PermissionsDescriptor listPermissions(String uuid, PermissionKind kind, boolean readable) {
        var node = findNode(uuid);
        permissionValidator.requirePermission(node, PermissionFlag.R);
        var result = new PermissionsDescriptor();
        result.setInheritance(Optional.ofNullable(node.getSecurityGroup()).map(SecurityGroup::isInheritanceEnabled).orElse(false));
        if (kind != PermissionKind.NONE) {
            var permissions = aclDAO.listPermissions(node, kind == PermissionKind.ALL, false);
            if (readable) {
                for (var p : permissions) {
                    p.setRights(PermissionFlag.formatAsHumanReadable(PermissionFlag.parse(p.getRights())));
                }
            }

            result.getPermissions().addAll(permissions);
        }

        return result;
    }

    private ActiveNode findNode(String uuid) {
        return nodeDAO
            .findNodeByUUID(uuid, Set.of(MapOption.DEFAULT, MapOption.SG), QueryScope.DEFAULT)
            .orElseThrow(() -> new NotFoundException("Unable to found node " + uuid));
    }

    private ActiveNode findManagedNode(String uuid) {
        var node = findNode(uuid);

        if (node.getSecurityGroup() == null || !node.getSecurityGroup().isManaged()) {
            throw new ForbiddenException("Forbidden operation on unmanaged SG");
        }

        return node;
    }

    private void alterInheritanceOnPaths(ApplicationTransaction tx, ActiveNode node) {
        var inheritance = node.getSecurityGroup().isInheritanceEnabled();
        if (inheritance) {
            TransactionService.current().connection(conn -> {
               var sql = """
                   select\s
                     c.node_path,
                     coalesce(c.sg_path, ':' || c.node_id || ':') child_sg_path,
                     coalesce(p.sg_path, ':' || p.node_id || ':') || c.node_id || ':' target_sg_path\s
                   from ecm_paths c\s
                   join ecm_paths p on (p.node_id = c.parent_id)\s
                   where c.node_id = ?
                   """;
               try (var stmt = conn.prepareStatement(sql)) {
                   stmt.setLong(1, node.getId());
                   try (var rs = stmt.executeQuery()) {
                       while (rs.next()) {
                           var path = rs.getString("node_path");
                           var sgPath = rs.getString("child_sg_path");
                           var targetPrefix = rs.getString("target_sg_path");
                           aclDAO.replaceSgPaths(tx.getId(), path, sgPath, targetPrefix);
                       }

                       return null;
                   }
               } catch (SQLException e) {
                   throw new SystemException(e);
               }
            });
        } else {
            var targetPrefix = ":" + node.getId() + ":";
            for (var path : node.getPaths()) {
                aclDAO.replaceSgPaths(tx.getId(), path.getPath(), path.getSgPath(), targetPrefix);
            }
        }
        //TODO: ottimizzare portandolo sull'aclDAO

        int n = pathDAO.propagatePathsTransaction(tx.getId());
        log.info("{} nodes updated", n);
    }
}

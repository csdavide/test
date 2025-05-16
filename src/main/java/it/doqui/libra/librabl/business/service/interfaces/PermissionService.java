package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.views.acl.PermissionItem;
import it.doqui.libra.librabl.views.acl.PermissionsDescriptor;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.Optional;

public interface PermissionService {

    /**
     * Adds permissions to a managed node.
     *
     * @param uuid The managed node identifier
     * @param permissions A collection of permission items
     * @throws it.doqui.libra.librabl.foundation.exceptions.NotFoundException if the node does not exist
     * @throws it.doqui.libra.librabl.foundation.exceptions.ForbiddenException if the node is unmanaged
     */
    void addPermissions(@NotNull String uuid, Collection<PermissionItem> permissions);

    /**
     * Replaces permissions to a managed node.
     *
     * @param uuid The managed node identifier
     * @param permissions A collection of permission items
     * @param inheritance Specifies if optionally alter the inheritance attribute too
     * @throws it.doqui.libra.librabl.foundation.exceptions.NotFoundException if the node does not exist
     * @throws it.doqui.libra.librabl.foundation.exceptions.ForbiddenException if the node is unmanaged
     */
    void replacePermissions(@NotNull String uuid, Collection<PermissionItem> permissions, @NotNull Optional<Boolean> inheritance);

    /**
     * Sets inheritance
     *
     * @param uuid The managed node identifier
     * @param inheritance Specifies if enable or disable inheritance
     * @throws it.doqui.libra.librabl.foundation.exceptions.NotFoundException if the node does not exist
     * @throws it.doqui.libra.librabl.foundation.exceptions.ForbiddenException if the node is unmanaged
     */
    void setInheritance(@NotNull String uuid, boolean inheritance);

    /**
     * Removes all permissions of a specified authority
     *
     * @param uuid The managed node identifier
     * @param authority Authority to remove
     * @throws it.doqui.libra.librabl.foundation.exceptions.NotFoundException if the node does not exist
     * @throws it.doqui.libra.librabl.foundation.exceptions.ForbiddenException if the node is unmanaged
     */
    void removeAllAuthorityPermissions(@NotNull String uuid, String authority);

    /**
     * Removes permissions from a managed node.
     *
     * @param uuid The managed node identifier
     * @param permissions A collection of permission items
     * @throws it.doqui.libra.librabl.foundation.exceptions.NotFoundException if the node does not exist
     * @throws it.doqui.libra.librabl.foundation.exceptions.ForbiddenException if the node is unmanaged
     */
    void removePermissions(@NotNull String uuid, Collection<PermissionItem> permissions);

    /**
     *
     * @param uuid The managed node identifier
     * @return {@code true} if the inheritance for the node is enabled, otherwise {@code false}
     */
    boolean isInheritance(@NotNull String uuid);

    /**
     * Returns the permissions on a managed node
     *
     * @param uuid The managed node identifier
     * @param kind If {@code ALL} includes inherited permissions available on upper security groups;
     *             otherwise {@code NODE} returns only permissions defined on the node.
     *             {@code NONE} returns only information on inheritance.
     * @param readable If {@code true} the rights is presented in a human-readable format
     * @return A descriptor of permission item on the node
     */
    PermissionsDescriptor listPermissions(@NotNull String uuid, PermissionKind kind, boolean readable);

    enum PermissionKind {
        NONE,
        NODE,
        ALL
    }
}

package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.dao.AclDAO;
import it.doqui.libra.librabl.business.provider.data.dao.NodeDAO;
import it.doqui.libra.librabl.business.provider.data.dao.PathDAO;
import it.doqui.libra.librabl.business.provider.data.dao.UserDAO;
import it.doqui.libra.librabl.business.provider.data.entities.User;
import it.doqui.libra.librabl.business.provider.data.entities.UserGroup;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.PerformResult;
import it.doqui.libra.librabl.business.service.core.TransactionService;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.business.service.interfaces.UserService;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.acl.PermissionItem;
import it.doqui.libra.librabl.views.acl.PermissionsDescriptor;
import it.doqui.libra.librabl.views.association.LinkItemRequest;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.LinkedInputNodeRequest;
import it.doqui.libra.librabl.views.security.DetailedUserItem;
import it.doqui.libra.librabl.views.security.EditableUserDescriptor;
import it.doqui.libra.librabl.views.security.UserItem;
import it.doqui.libra.librabl.views.security.UserRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_NAME;

@ApplicationScoped
@Slf4j
public class UserManager implements UserService {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS");

    @ConfigProperty(name = "libra.authentication.new-password-alg", defaultValue = "MD5")
    String defaultAlg;

    @ConfigProperty(name = "libra.paths.user-home", defaultValue = "/app:company_home/app:user_homes/")
    String userHomesPath;

    @Inject
    NodeManager nodeService;

    @Inject
    UserDAO userDAO;

    @Inject
    AclDAO aclDAO;

    @Inject
    NodeDAO nodeDAO;

    @Inject
    PathDAO pathDAO;

    @Override
    public Paged<String> findGroups(String groupname, Pageable pageable) {
        final String prefix;
        if (StringUtils.isBlank(groupname) || "*".equals(groupname)) {
            prefix = null;
        } else {
            if (StringUtils.contains(groupname, '@')) {
                throw new BadRequestException("Group name cannot contain the character '@'");
            }

            prefix = groupname.replace("*", "%").toUpperCase();
        }

        var p = userDAO.findGroups(prefix, pageable);
        return p.map(UserGroup::getGroupname);
    }

    @Override
    public Paged<UserItem> findUsers(String username, boolean includeMetadata, boolean nameOnly, Pageable pageable) {
        final String usernamePrefix;
        if (StringUtils.isBlank(username) || "*".equals(username)) {
            usernamePrefix = null;
        } else {
            String[] s = username.split("@");
            if (s.length > 1 && !StringUtils.equalsIgnoreCase(s[1], UserContextManager.getTenant())) {
                throw new ForbiddenException("Tenant does not match");
            }
            usernamePrefix = s[0].replace("*", "%");
        }

        var p = userDAO.findUsers(usernamePrefix, null, pageable);
        return map(p, includeMetadata, nameOnly);
    }

    @Override
    public DetailedUserItem findUser(String username) {
        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        final String[] uname = username.split("@");
        if (uname.length > 1 && !StringUtils.equalsIgnoreCase(uname[1], tenantRef.getName())) {
            throw new ForbiddenException("Tenant does not match");
        }

        var user = userDAO.findUser(uname[0], true).orElseThrow(() -> new NotFoundException(username));
        var pathMap = pathDAO.pathOfUUIDs(Optional.ofNullable(user.getData().getHome()).map(List::of).orElse(null));
        return map(user, new DetailedUserItem(), tenantRef, pathMap,true, false);
    }

    @Override
    public Paged<UserItem> findGroupUsers(String groupname, boolean includeMetadata, boolean nameOnly, Pageable pageable) {
        if (StringUtils.contains(groupname, '@')) {
            throw new BadRequestException("Group name cannot contain the character '@'");
        }

        final String name = StringUtils.upperCase(groupname);
        UserGroup g = userDAO
            .findGroup(name)
            .orElseThrow(() -> new NotFoundException(String.format("Group %s not found", name)));

        var p = userDAO.findUsers(null, g.getId(), pageable);
        return map(p, includeMetadata, nameOnly);
    }

    private Paged<UserItem> map(Paged<User> p, boolean includeMetadata, boolean nameOnly) {
        final Map<String,List<String>> pathMap;
        if (includeMetadata) {
            pathMap = pathDAO.pathOfUUIDs(
                p.getItems()
                    .stream()
                    .map(u -> u.getData().getHome())
                    .filter(Objects::nonNull)
                    .toList()
            );
        } else {
            pathMap = null;
        }

        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        return p.map(u -> asUserItem(u, tenantRef, pathMap, includeMetadata, nameOnly));
    }

    @Transactional
    @Override
    public String createGroup(String groupname) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        UserGroup g = normalizeGroup(groupname, false);
        try {
            userDAO.createGroup(g);
        } catch (SystemException e) {
            throw new ConflictException(String.format("Unable to create group %s: %s", g.getGroupname(), e.getMessage()));
        }

        return g.getGroupname();
    }

    private UserGroup normalizeGroup(String groupname, boolean ignoreEveryone) {
        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        groupname = StringUtils.upperCase(groupname);
        final String name = (groupname.startsWith("GROUP_") ? groupname.substring(6) : groupname);
        if (StringUtils.equals(name, "EVERYONE")) {
            if (ignoreEveryone) {
                return null;
            }

            throw new ForbiddenException("GROUP_EVERYONE already exists");
        } else if (StringUtils.contains(name, '@')) {
            throw new BadRequestException("Group name cannot contain the character '@'");
        }

        UserGroup g = new UserGroup();
        g.setTenant(tenantRef.toString());
        g.setGroupname(name);

        return g;
    }

    @Override
    public UserItem createUser(UserRequest u) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        String[] uname = u.getUsername().split("@");

        if (StringUtils.isBlank(uname[0])) {
            throw new BadRequestException("Username cannot be blank");
        }

        if (uname.length > 1 && !StringUtils.equalsIgnoreCase(uname[1], tenantRef.toString())) {
            throw new ForbiddenException("Tenant username part does not match");
        }

        u.getRoles().stream()
            .filter(role -> StringUtils.equalsIgnoreCase(role, UserContext.ROLE_SYSADMIN))
            .findAny()
            .ifPresent(role -> {
                throw new ForbiddenException("No permission to set sysadmin role");
            });

        return TransactionService.current().perform(tx -> {
            userDAO
                .findUser(uname[0])
                .ifPresent(g -> {
                    throw new ConflictException(String.format("User %s already exists", uname[0]));
                });

            var homesUUID = nodeDAO
                .findUUIDWherePath(userHomesPath)
                .orElseThrow(() -> new RuntimeException("Unable to locate users home node"));

            final User user = new User();
            user.setUsername(uname[0]);
            user.setTenant(tenantRef.toString());
            user.setUuid(UUID.randomUUID().toString());
            user.getData().setEnabled(true);
            user.getData().setFirstName(u.getFirstName());
            user.getData().setLastName(u.getLastName());
            user.getData().getRoles().addAll(u.getRoles());
            if (u.getPassword() != null && u.getPassword().length > 0) {
                try {
                    user.getData().setAlg(defaultAlg);
                    String hashed = ObjectUtils.hash(u.getPassword(), defaultAlg);
                    user.getData().setPassword(hashed);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }

            final Map<String, List<String>> pathMap;
            if (u.isHomeRequired()) {
                AuthorityRef authorityRef = new AuthorityRef(user.getUsername(), tenantRef);

                // home perms
                var userPerm = new PermissionItem();
                userPerm.setAuthority(authorityRef.toString());
                userPerm.setRights(PermissionFlag.formatAsBinary(PermissionFlag.parse("RWCDA")));
                var pd = new PermissionsDescriptor();
                pd.setInheritance(true);
                pd.getPermissions().add(userPerm);

                // create user home
                var home = new LinkedInputNodeRequest();
                home.setTypeName(Constants.CM_FOLDER);
                home.getProperties().put(CM_NAME, authorityRef.toString());
                home.getProperties().put(Constants.CM_OWNER, authorityRef.toString());
                home.getAspects().add(Constants.ASPECT_CM_OWNABLE);
                home.setPermissionsDescriptor(pd);

                var homeLink = new LinkItemRequest();
                homeLink.setRelationship(RelationshipKind.PARENT);
                homeLink.setVertexUUID(homesUUID);
                homeLink.setTypeName(Constants.CM_CONTAINS);
                homeLink.setName("sys:" + authorityRef);
                homeLink.setHard(true);
                home.getAssociations().add(homeLink);

                var node = nodeService.createNode(tx, home);
                user.getData().setHome(node.getUuid());
                pathMap = pathDAO.pathOfUUIDs(List.of(node.getUuid()));
            } else {
                pathMap = null;
            }

            userDAO.createUser(user, false);
            if (!u.getGroups().isEmpty()) {
                var processedGroups = new HashSet<String>();
                for (var groupname : u.getGroups()) {
                    var g = normalizeGroup(groupname, true);
                    if (g != null && !processedGroups.contains(g.getGroupname())) {
                        userDAO.findGroup(g.getGroupname()).ifPresentOrElse(x -> g.setId(x.getId()), () -> userDAO.createGroup(g));
                        userDAO.addUserToGroup(user.getId(), g.getId());
                        processedGroups.add(g.getGroupname());
                    }
                }
            }

            return PerformResult.<UserItem>builder()
                .result(asUserItem(user, tenantRef, pathMap, true, false))
                .mode(PerformResult.Mode.SYNC)
                .count(1)
                .priorityUUIDs(Optional.ofNullable(pathMap).map(Map::keySet).orElse(null))
                .build();
        });
    }

    @Override
    public Collection<UserItem> createUsers(Collection<UserRequest> users) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        return TransactionService.current().perform(tx -> {
            var createdUsers = new ArrayList<UserItem>();
            for (var u : users) {
                var user = createUser(u);
                createdUsers.add(user);
            }

            return PerformResult.<Collection<UserItem>>builder()
                .result(createdUsers)
                .mode(PerformResult.Mode.SYNC)
                .count(createdUsers.size())
                .build();
        });
    }

    @Transactional
    @Override
    public void updateUser(String username, EditableUserDescriptor item) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        final String[] uname = username.split("@");
        if (uname.length > 1 && !StringUtils.equalsIgnoreCase(uname[1], tenantRef.getName())) {
            throw new ForbiddenException("Tenant does not match");
        }

        var u = userDAO.findUser(uname[0])
            .orElseThrow(() -> new NotFoundException(username));

        Optional.ofNullable(item.getPassword())
            .ifPresent(password -> u.getData().setPassword(password.map(value -> {
                try {
                    return ObjectUtils.hash(value, Optional.ofNullable(u.getData().getAlg()).orElse(defaultAlg));
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }).orElse(null)));

        u.getData().setFirstName(ObjectUtils.getIfDefined(item.getFirstName(), u.getData().getFirstName(), false));
        u.getData().setLastName(ObjectUtils.getIfDefined(item.getLastName(), u.getData().getLastName(), false));
        u.getData().setEnabled(ObjectUtils.getIfDefined(item.getEnabled(), u.getData().getEnabled(), false));
        userDAO.updateUser(u);
    }

    @Transactional
    @Override
    public void addUserToGroup(String username, String groupname) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        final String[] uname = username.split("@");
        if (uname.length > 1 && !StringUtils.equalsIgnoreCase(uname[1], tenantRef.getName())) {
            throw new ForbiddenException("Tenant does not match");
        }

        var g = normalizeGroup(groupname, true);
        if (g != null) {
            UserGroup group = userDAO
                .findGroup(g.getGroupname())
                .orElseThrow(() -> new NotFoundException(String.format("Group %s not found", g.getGroupname())));

            User user = userDAO
                .findUser(uname[0])
                .orElseThrow(() -> new NotFoundException(String.format("User %s not found", uname[0])));

            userDAO.addUserToGroup(user.getId(), group.getId());
        }

    }

    @Transactional
    @Override
    public void removeUserFromGroup(String username, String groupname) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        groupname = StringUtils.upperCase(groupname);
        final String name = (groupname.startsWith("GROUP_") ? groupname.substring(6) : groupname);
        if (StringUtils.equals(name, "EVERYONE")) {
            throw new ForbiddenException("None cannot be removed from GROUP_EVERYONE");
        }

        if (StringUtils.contains(groupname, '@')) {
            throw new BadRequestException("Group name cannot contain the character '@'");
        }

        final String[] uname = username.split("@");
        if (uname.length > 1 && !StringUtils.equalsIgnoreCase(uname[1], tenantRef.getName())) {
            throw new ForbiddenException("Tenant does not match");
        }

        UserGroup group = userDAO
            .findGroup(name)
            .orElseThrow(() -> new NotFoundException(String.format("Group %s not found", name)));

        User user = userDAO
            .findUser(uname[0])
            .orElseThrow(() -> new NotFoundException(String.format("User %s not found", uname[0])));

        if (!userDAO.removeUserFromGroup(user.getId(), group.getId())) {
            throw new PreconditionFailedException(String.format("User %s not in group %s", uname[0], groupname));
        }
    }

    @Override
    public void deleteGroup(String groupname) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        groupname = StringUtils.upperCase(groupname);
        final String name = (groupname.startsWith("GROUP_") ? groupname.substring(6) : groupname);
        if (StringUtils.equals(name, "EVERYONE")) {
            throw new ForbiddenException("GROUP_EVERYONE cannot be deleted");
        }

        TransactionService.current().perform(tx -> {
            var group = userDAO
                .findGroup(name)
                .orElseThrow(() -> new NotFoundException(String.format("Group %s not found", name)));

            userDAO.deleteGroup(group.getId());
            int n = aclDAO.removeAuthority(tx.getId(), group.getGroupname());
            log.info("Group {} deleted: {} security groups updated", group.getGroupname(), n);

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.ASYNC)
                .build();
        });
    }

    @Override
    public void deleteUser(String username) {
        if (!UserContextManager.getContext().isAdmin()) {
            throw new ForbiddenException("Admin role required");
        }

        final TenantRef tenantRef = UserContextManager.getContext().getTenantRef();
        final String[] uname = username.split("@");
        if (uname.length > 1 && !StringUtils.equalsIgnoreCase(uname[1], tenantRef.getName())) {
            throw new ForbiddenException("Tenant does not match");
        }

        if (StringUtils.equalsIgnoreCase(uname[0], "admin")) {
            throw new ForbiddenException("admin cannot be deleted");
        }

        TransactionService.current().perform(tx -> {
            var user = userDAO
                .findUser(uname[0])
                .orElseThrow(() -> new NotFoundException(String.format("User %s not found", uname[0])));
            userDAO.deleteUser(user.getId());

            var authorityRef = new AuthorityRef(user.getUsername(), tenantRef);
            var home = user.getData().getHome();
            if (home != null) {
                // unname the old sg of the home
                aclDAO.renameSecurityGroupWhereName(user.getUsername(), null);

                // rename the old user home
                var name = String.format("sys:%s_%s", authorityRef, dateFormatter.format(ZonedDateTime.now()));
                log.debug("Renaming home {} as {}", home, name);
                nodeService.renameNode(home, name, LinkMode.ALL);
            }

            int n = aclDAO.removeAuthority(tx.getId(), authorityRef.toString());
            log.info("User {} deleted: {} security groups updated", authorityRef, n);

            return PerformResult.<Void>builder()
                .mode(PerformResult.Mode.ASYNC)
                .build();
        });
    }

    private UserItem asUserItem(User u, TenantRef tenantRef, Map<String,List<String>> pathMap, boolean includeMetadata, boolean nameOnly) {
        return map(u, new UserItem(), tenantRef, pathMap, includeMetadata, nameOnly);
    }

    private <T extends UserItem> T map(User u, T x, TenantRef tenantRef, Map<String,List<String>> pathMap, boolean includeMetadata, boolean nameOnly) {
        x.setUsername(nameOnly ? u.getUsername() : new AuthorityRef(u.getUsername(), tenantRef).toString());
        x.setEnabled(Optional.ofNullable(u.getData().getEnabled()).orElse(true));
        x.setLocked(u.getData().isLocked());

        if (includeMetadata) {
            x.setFirstName(u.getData().getFirstName());
            x.setLastName(u.getData().getLastName());
            x.setHomeUUID(u.getData().getHome());
            x.getRoles().addAll(u.getData().getRoles());

            if (u.getData().getHome() != null && pathMap != null) {
                var list = pathMap.get(u.getData().getHome());
                if (list != null) {
                    x.setHomePath(list.stream().findFirst().orElse(null));
                }
            }

            if (x instanceof DetailedUserItem d) {
                d.getGroups().addAll(u.getGroups().stream().map(UserGroup::getGroupname).toList());
            }
        }

        return x;
    }
}

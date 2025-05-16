package it.doqui.libra.librabl.business.provider.data.dao;

import it.doqui.libra.librabl.business.provider.data.entities.*;
import it.doqui.libra.librabl.business.provider.security.AuthorityUtils;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.node.PermissionFlag;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.acl.EditableSecurityGroup;
import it.doqui.libra.librabl.views.acl.InheritablePermissionItem;
import it.doqui.libra.librabl.views.acl.PermissionItem;
import it.doqui.libra.librabl.views.acl.PermissionsDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static it.doqui.libra.librabl.business.service.auth.UserContext.GROUP_EVERYONE;

@ApplicationScoped
@Slf4j
public class AclDAO extends AbstractDAO {

    public SecurityGroup createUnmanagedSG(ApplicationTransaction tx, EditableSecurityGroup s) {
        var sg = new SecurityGroup();
        sg.setTenant(UserContextManager.getTenant());
        sg.setUuid(UUID.randomUUID().toString());
        sg.setName(ObjectUtils.get(s.getName()));
        sg.setManaged(false);
        sg.setInheritanceEnabled(false);
        sg.setTx(tx);
        addPermissions(sg, s.getPermissions());
        createSecurityGroup(sg);
        return sg;
    }

    public SecurityGroup createManagedSG(ApplicationTransaction tx, PermissionsDescriptor pd) {
        var sg = new SecurityGroup();
        sg.setTenant(UserContextManager.getTenant());
        sg.setManaged(true);
        sg.setTx(tx);

        if (pd != null) {
            sg.setInheritanceEnabled(ObjectUtils.getAsBoolean(pd.getInheritance(), true));
            addPermissions(sg, pd.getPermissions());
        } else {
            sg.setInheritanceEnabled(true);
        }

        createSecurityGroup(sg);
        return sg;
    }

    private void addPermissions(SecurityGroup sg, Collection<PermissionItem> permissions) {
        if (permissions == null) {
            return;
        }

        var tenantRef = TenantRef.valueOf(sg.getTenant());
        var arList = map(tenantRef, permissions);
        arList.forEach(ar -> {
            ar.setSecurityGroup(sg);
            sg.getRules().add(ar);
        });
    }

    private Collection<AccessRule> map(TenantRef tenantRef, Collection<PermissionItem> permissions) {
        return permissions.stream().map(p -> {
                if (StringUtils.isNotBlank(p.getAuthority()) && StringUtils.isNotBlank(p.getRights())) {
                    var ar = new AccessRule();
                    ar.setAuthority(AuthorityUtils.normalizeAuthority(p.getAuthority(), tenantRef));
                    ar.setRights(PermissionFlag.formatAsBinary(PermissionFlag.parse(p.getRights())));
                    return ar;
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    public Collection<InheritablePermissionItem> listPermissions(ActiveNode node, boolean includeInherited, boolean mineOnly) {
        return call(conn -> {
            var sql = """
                select n.id,r.authority,r.rights from ecm_access_rules r\s
                join ecm_nodes n on (n.sg_id = r.sg_id)\s
                where\s
                """;

            final List<Long> nodeIDs;
            if (includeInherited) {
                if (node.getPaths().isEmpty()) {
                    nodeIDs = null;
                    sql += """
                      n.id in (
                        select unnest(array_remove(string_to_array(coalesce(sg_path,':' || node_id || ':'), ':'),''))::int\s
                        from ecm_paths where node_id = ?
                      )\s
                      """;
                } else {
                    nodeIDs = node.getPaths()
                        .stream()
                        .map(NodePath::getSgPath)
                        .map(s -> ObjectUtils.coalesce(s, ":" + node.getId() + ":"))
                        .filter(Objects::nonNull)
                        .flatMap(s -> Arrays.stream(s.split(":")))
                        .filter(StringUtils::isNotBlank)
                        .map(Long::parseLong)
                        .distinct()
                        .toList();

                    if (!nodeIDs.isEmpty()) {
                        sql += "n.id = any (?) ";
                    } else {
                        sql += "n.id = ? ";
                    }
                }
            } else {
                nodeIDs = null;
                sql += "n.id = ? ";
            }

            if (mineOnly) {
                sql += """
                    and (r.authority = ? or r.authority = ? or r.authority in\s
                    (select g.groupname from ecm_users u\s
                    join ecm_user_groups ug on (ug.user_id = u.id)\s
                    join ecm_groups g on (g.id = ug.group_id)\s
                    where u.tenant = ? and lower(u.username) = ?))\s
                    """;
            }

            try (var stmt = conn.prepareStatement(sql)) {
                if (nodeIDs == null || nodeIDs.isEmpty()) {
                    stmt.setLong(1, node.getId());
                } else {
                    stmt.setArray(1, conn.createArrayOf("INTEGER", nodeIDs.toArray(new Long[0])));
                }

                if (mineOnly) {
                    var authorityRef = UserContextManager.getContext().getAuthorityRef();
                    stmt.setString(2, authorityRef.toString());
                    stmt.setString(3, GROUP_EVERYONE);
                    stmt.setString(4, authorityRef.getTenantRef().toString());
                    stmt.setString(5, authorityRef.getIdentity().toLowerCase());
                }

                try (var rs = stmt.executeQuery()) {
                    var permissions = new ArrayList<InheritablePermissionItem>();
                    while (rs.next()) {
                        var p = new InheritablePermissionItem();
                        p.setAuthority(rs.getString("authority"));
                        p.setRights(rs.getString("rights"));
                        p.setInherited(rs.getLong("id") != node.getId());
                        permissions.add(p);
                    }
                    return permissions;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void replaceSgPaths(long txId, String pathPrefix, String sourcePrefix, String targetPrefix) {
        call(conn -> {
            try {
                var parts = Arrays.stream(pathPrefix.split(":"))
                    .filter(StringUtils::isNotBlank)
                    .map(Long::parseLong)
                    .toList()
                    .toArray(new Long[0]);

                try (var stmt = conn.prepareStatement("""
                    update ecm_paths set tx = ?, sg_path = replace(sg_path, ?, ?)\s
                    where path_parts @> ? and sg_path like ?
                    """)) {
                    stmt.setLong(1, txId);
                    stmt.setString(2, sourcePrefix);
                    stmt.setString(3, targetPrefix);
                    stmt.setArray(4, conn.createArrayOf("INTEGER", parts));
                    stmt.setString(5, sourcePrefix + "%");
                    stmt.executeUpdate();
                }

                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Paged<SecurityGroup> findSecurityGroups(String namePrefix, boolean includeRules, Pageable pageable) {
        return call(conn -> {
            final var fields = "id,tenant,uuid,name,tx,inheritance,managed";
            final var order = "id";
            var sql = """
            select {fields}\s
            from ecm_security_groups\s
            where tenant = ?
            """;

            if (StringUtils.isNotBlank(namePrefix)) {
                sql += " and name "
                    + (namePrefix.contains("%") ? "like" : "=")
                    + " ?";
            }

            try {
                var p = find(conn, sql, fields, order, pageable,
                    stmt -> {
                        try {
                            var c = 0;
                            stmt.setString(++c, UserContextManager.getTenant());
                            if (StringUtils.isNotBlank(namePrefix)) {
                                stmt.setString(++c, namePrefix);
                            }
                            return c;
                        } catch (SQLException e) {
                            throw new SystemException(e);
                        }
                    },
                    rs -> {
                        try {
                            var sg = new SecurityGroup();
                            fillSecurityGroup(rs, sg);
                            return sg;
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });

                if (includeRules) {
                    var sgMap = new HashMap<Long,SecurityGroup>();
                    for (var sg : p.getItems()) {
                        sgMap.put(sg.getId(), sg);
                    }

                    retrieveAndFillRules(conn, sgMap);
                }

                return p;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<SecurityGroup> findSecurityGroup(String sgid, boolean includeRules) {
        return call(conn -> {
            try {
                var sgSQL = """
                    select id,tenant,uuid,name,tx,inheritance,managed\s
                    from ecm_security_groups\s
                    where tenant = ? and uuid = ?
                    """;
                try (var stmt = conn.prepareStatement(sgSQL)) {
                    stmt.setString(1, UserContextManager.getTenant());
                    stmt.setString(2, sgid);
                    try (var rs = stmt.executeQuery()){
                        if (rs.next()) {
                            var sg = new SecurityGroup();
                            fillSecurityGroup(rs, sg);

                            if (includeRules) {
                                retrieveAndFillRules(conn, sg);
                            }

                            return Optional.of(sg);
                        }

                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<SecurityGroup> findSecurityGroup(long id, boolean includeRules) {
        return call(conn -> {
            try {
                var sgSQL = """
                    select id,tenant,uuid,name,tx,inheritance,managed\s
                    from ecm_security_groups\s
                    where id = ?
                    """;
                try (var stmt = conn.prepareStatement(sgSQL)) {
                    stmt.setString(1, UserContextManager.getTenant());
                    stmt.setLong(1, id);
                    try (var rs = stmt.executeQuery()){
                        if (rs.next()) {
                            var sg = new SecurityGroup();
                            fillSecurityGroup(rs, sg);

                            if (includeRules) {
                                retrieveAndFillRules(conn, sg);
                            }

                            return Optional.of(sg);
                        }

                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private void fillSecurityGroup(ResultSet rs, SecurityGroup sg) throws SQLException {
        sg.setId(rs.getLong("id"));
        sg.setTenant(rs.getString("tenant"));
        sg.setUuid(rs.getString("uuid"));
        sg.setName(rs.getString("name"));

        var txId = DBUtils.getLong(rs,"tx");
        if (txId != null) {
            var tx = new ApplicationTransaction();
            tx.setId(txId);
            sg.setTx(tx);
        }

        sg.setInheritanceEnabled(rs.getBoolean("inheritance"));
        sg.setManaged(rs.getBoolean("managed"));
    }

    private void retrieveAndFillRules(Connection conn, SecurityGroup sg) throws SQLException {
        var sql = """
            select id,authority,rights from ecm_access_rules\s
            where sg_id = ?
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, sg.getId());
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sg.getRules().add(readAccessRule(rs));
                }
            }
        }
    }

    private void retrieveAndFillRules(Connection conn, Map<Long,SecurityGroup> sgMap) throws SQLException {
        var sql = """
            select id,authority,rights,sg_id from ecm_access_rules\s
            where sg_id = any (?)
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", sgMap.keySet().toArray(new Long[0])));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var ar = readAccessRule(rs);
                    var sgId = rs.getLong("sg_id");
                    sgMap.compute(sgId, (k,v) -> {
                        if (v == null) {
                            return null;
                        }

                        v.getRules().add(ar);
                        return v;
                    });
                }
            }
        }
    }

    private AccessRule readAccessRule(ResultSet rs) throws SQLException {
        var ar = new AccessRule();
        ar.setId(rs.getLong("id"));
        ar.setAuthority(rs.getString("authority"));
        ar.setRights(rs.getString("rights"));
        return ar;
    }

    public void createSecurityGroup(SecurityGroup sg) {
        call(conn -> {
            try {
                var sgSQL = """
                    insert into ecm_security_groups (tenant,uuid,name,tx,inheritance,managed)\s
                    values (?,?,?,?,?,?)
                    """;
                try (var stmt = conn.prepareStatement(sgSQL, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, sg.getTenant());
                    stmt.setString(2, sg.getUuid());
                    stmt.setString(3, sg.getName());
                    DBUtils.setLong(stmt, 4, Optional.ofNullable(sg.getTx()).map(ApplicationTransaction::getId).orElse(null));
                    stmt.setBoolean(5, sg.isInheritanceEnabled());
                    stmt.setBoolean(6, sg.isManaged());

                    if (stmt.executeUpdate() < 1) {
                        throw new RuntimeException("Unable to create security group");
                    }

                    try (var generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            sg.setId(generatedKeys.getLong(1));
                        } else {
                            throw new RuntimeException("SG creation failed, no ID obtained.");
                        }
                    }
                }

                addAccessRules(conn, sg.getId(), sg.getRules());
                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void addAccessRules(long sgId, TenantRef tenantRef, Collection<PermissionItem> permissions) {
        if (permissions == null) {
            return;
        }

        addAccessRules(sgId, map(tenantRef, permissions));
    }

    public void addAccessRules(long sgId, Collection<AccessRule> rules) {
        call(conn -> {
            try {
                addAccessRules(conn, sgId, rules);
                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private void addAccessRules(Connection conn, long sgId, Collection<AccessRule> rules) throws SQLException {
        var arSQL = """
            insert into ecm_access_rules (authority,rights,sg_id)\s
            values (?,?,?)
            """;
        try (var stmt = conn.prepareStatement(arSQL)) {
            stmt.setLong(3, sgId);
            for (var ar : rules) {
                stmt.setString(1, ar.getAuthority());
                stmt.setString(2, ar.getRights());

                if (stmt.executeUpdate() < 1) {
                    throw new RuntimeException("Unable to create access rule for SG " + sgId);
                }
            }
        }
    }

    public void setTx(long sgId, long txId) {
        call(conn -> {
            var sql = """
                update ecm_security_groups set tx = ?\s
                where id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, txId);
                stmt.setLong(2, sgId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });
    }

    public void updateInheritance(long sgId, boolean inheritance, long txId) {
        call(conn -> {
            var sql = """
                update ecm_security_groups set tx = ?, inheritance = ?\s
                where id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, txId);
                stmt.setBoolean(2, inheritance);
                stmt.setLong(3, sgId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });
    }

    public void renameSecurityGroupWhereName(String oldName, String newName) {
        call(conn -> {
            var sql = """
                update ecm_security_groups set name = ?\s
                where tenant = ? and name = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newName);
                stmt.setString(2, UserContextManager.getTenant());
                stmt.setString(3, oldName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });
    }

    public void renameSecurityGroupWhereId(long id, String newName) {
        call(conn -> {
            var sql = """
                update ecm_security_groups set name = ?\s
                where id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newName);
                stmt.setLong(2, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });
    }

    public int removeAuthority(long txId, String authority) {
        return call(conn -> {
            try {
                if (setTxWhereAuthority(conn, txId, authority) > 0) {
                    var sql = """
                        delete from ecm_access_rules\s
                        where authority = ?
                        """;
                    try (var stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, authority);
                        return stmt.executeUpdate();
                    }
                } else {
                    return 0;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void removeAllSecurityGroupRules(long sgId) {
        call(conn -> {
            try (var stmt = conn.prepareStatement("delete from ecm_access_rules where sg_id = ?")) {
                stmt.setLong(1, sgId);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void deleteAuthorityFromSG(long sgId, String authority) {
        call(conn -> {
            try (var stmt = conn.prepareStatement("delete from ecm_access_rules where sg_id = ? and authority = ?")) {
                stmt.setLong(1, sgId);
                stmt.setString(2, authority);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public int deleteAccessRules(Collection<AccessRule> rules) {
        return call(conn -> {
            try (var stmt = conn.prepareStatement("delete from ecm_access_rules where id = ?")) {
                int count = 0;
                for (var ar : rules) {
                    stmt.setLong(1, ar.getId());
                    count += stmt.executeUpdate();
                }

                return count;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void removePermissions(long sgId, Collection<PermissionItem> permissions) {
        var sg = findSecurityGroup(sgId, true)
            .orElseThrow(() -> new PreconditionFailedException("SG " + sgId + " not found"));

        var tenantRef = TenantRef.valueOf(sg.getTenant());
        var deletingRules = new LinkedList<AccessRule>();
        for (var permission : permissions) {
            var a = AuthorityUtils.normalizeAuthority(permission.getAuthority(), tenantRef);
            var r = PermissionFlag.parse(permission.getRights());
            var rules = sg.getRules().stream()
                .filter(ar -> StringUtils.equalsIgnoreCase(ar.getAuthority(), a))
                .filter(ar -> PermissionFlag.parse(ar.getRights()) == r)
                .toList();
            deletingRules.addAll(rules);
            sg.getRules().removeAll(rules);
        }

        int n = deleteAccessRules(deletingRules);
        log.debug("{} access rules deleted", n);
    }

    private int setTxWhereAuthority(Connection conn, long txId, String authority) throws SQLException {
        var sql = """
            update ecm_security_groups set tx = ?\s
            where id in (select sg_id from ecm_access_rules where authority = ?)
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, txId);
            stmt.setString(2, authority);
            return stmt.executeUpdate();
        }
    }
}

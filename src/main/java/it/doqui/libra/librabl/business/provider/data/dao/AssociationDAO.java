package it.doqui.libra.librabl.business.provider.data.dao;

import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.Association;
import it.doqui.libra.librabl.business.provider.data.entities.SecurityGroup;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.exceptions.*;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import it.doqui.libra.librabl.views.node.DeleteMode;
import it.doqui.libra.librabl.views.tenant.TenantData;
import it.doqui.libra.librabl.views.tenant.TenantSpace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.PATH_FLAG;
import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.formatAsBinary;
import static it.doqui.libra.librabl.views.association.RelationshipKind.*;

@ApplicationScoped
@Slf4j
public class AssociationDAO extends AbstractDAO {

    @ConfigProperty(name = "libra.delete.deferred", defaultValue = "false")
    boolean deleteDeferred;

    @Inject
    TenantDataManager tenantDataManager;

    public void copyChildrenAssociations(ActiveNode source, ActiveNode target) {
        call(conn -> {
            var sql = """
                insert into ecm_associations (parent_id,child_id,type_name,name,code,is_hard)\s
                select ?,child_id,type_name,name,code,case when is_hard is not null then false else is_hard end\s
                from ecm_associations\s
                where parent_id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, target.getId());
                stmt.setLong(2, source.getId());
                int n = stmt.executeUpdate();
                log.debug("{} child associations copied from {} to {}", n, source.getId(), target.getId());
                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void rebuildPathsWhereNodeTx(long txId, Collection<Long> excludedNodes) {
        long rootId = tenantDataManager
            .findByIdOptional(UserContextManager.getContext().getTenantRef().toString())
            .map(TenantSpace::getRootId)
            .orElseThrow(() -> new PreconditionFailedException("No root found for tenant " + UserContextManager.getContext().getTenantRef().toString()));
        rebuildPathsWhereNodeTx(txId, rootId, excludedNodes);
    }

    public void rebuildPathsWhereNodeTx(long txId, long root, Collection<Long> excludedNodes) {
        call(conn -> {
            try {
                rebuildPathsWhereNodeTx(conn, txId, root, excludedNodes);
                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private void rebuildPathsWhereNodeTx(Connection conn, long txId, long root, Collection<Long> excludedNodes) throws SQLException {
        try (var stmt = conn.prepareStatement("""
                with recursive build_paths(node_id,fpath,npath,is_hard,sg_path,last_inheritance,root_id,parent_id,lev) as (\s
                    select n.id,
                        '/'||a.name||'/',
                        ':'||n.id|| ':',
                        a.is_hard,
                        ':'||n.id|| ':',
                        coalesce(s.inheritance, false),
                        a.parent_id,
                        a.parent_id,
                        1\s
                    from ecm_nodes n\s
                        left outer join ecm_security_groups s on (n.sg_id = s.id)\s
                        join ecm_associations a on (a.child_id = n.id and a.is_hard is not null)\s
                    where n.tx = ? and case when ? then n.id != any(?) else true end\s
                union\s
                    select b.node_id,
                        '/'||a.name||b.fpath,
                        ':'||n.id||b.npath,
                        a.is_hard and b.is_hard,
                        case when not b.last_inheritance then b.sg_path else ':' || n.id || b.sg_path end,
                        case when not b.last_inheritance then false else coalesce(s.inheritance, false) end,
                        a.parent_id,
                        b.parent_id,
                        b.lev + 1\s
                    from build_paths b\s
                        join ecm_nodes n on (n.id = b.root_id)\s
                        left outer join ecm_security_groups s on (n.sg_id = s.id)\s
                        join ecm_associations a on (a.child_id = b.root_id and a.is_hard is not null)\s
                )\s
                insert into ecm_paths (node_id,file_path,node_path,sg_path,is_hard,parent_id,tx,lev,path_parts)\s
                select node_id, fpath file_path, ':'||root_id||npath node_path, sg_path, is_hard, parent_id, ?, lev+1,
                func_path_parts(':'||root_id||npath)\s
                from build_paths\s
                where root_id = ?\s
                on conflict (node_path) do nothing
                """))
        {
            stmt.setLong(1, txId);
            stmt.setBoolean(2, excludedNodes != null);
            stmt.setArray(3, conn.createArrayOf("INTEGER", (excludedNodes != null ? excludedNodes.toArray(new Long[0]) : null)));
            stmt.setLong(4, txId);
            stmt.setLong(5, root);
            int n = stmt.executeUpdate();
            log.debug("{} paths rebuilt", n);
        }
    }

    public Paged<Association> findAssociations(ActiveNode node, RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable) {
        return call(conn -> findAssociations(conn, node, relationship, filterAssociationTypes, filterNodeTypes, pageable));
    }

    private Paged<Association> findAssociations(Connection conn, ActiveNode node, RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable) {
        try {
            var totalElements = 0;
            if (pageable != null) {
                try (var stmt = conn.prepareStatement(sqlToFindAssociations(relationship, filterAssociationTypes, filterNodeTypes, pageable, true))) {
                    setParamsToFindAssociations(stmt, node, relationship, filterAssociationTypes, filterNodeTypes, pageable, true);
                    try (var rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            totalElements = rs.getInt(1);
                        }
                    }
                }
            }

            var items = new LinkedList<Association>();
            try (var stmt = conn.prepareStatement(sqlToFindAssociations(relationship, filterAssociationTypes, filterNodeTypes, pageable, false))) {
                setParamsToFindAssociations(stmt, node, relationship, filterAssociationTypes, filterNodeTypes, pageable, false);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        var a = new Association();
                        a.setId(rs.getLong("id"));
                        a.setHard(DBUtils.getBoolean(rs, "is_hard"));
                        a.setTypeName(rs.getString("type_name"));
                        a.setName(rs.getString("name"));
                        a.setCode(rs.getString("code"));

                        var parent = new ActiveNode();
                        parent.setId(rs.getLong("parent_id"));
                        parent.setTenant(node.getTenant());
                        parent.setUuid(rs.getString("parent_uuid"));

                        var child = new ActiveNode();
                        child.setId(rs.getLong("child_id"));
                        child.setTenant(node.getTenant());
                        child.setUuid(rs.getString("child_uuid"));

                        a.setChild(child);
                        a.setParent(parent);
                        items.add(a);
                    }
                }
            }

            if (pageable == null) {
                return new Paged<>(items);
            } else {
                return new Paged<>(pageable.getPage(), pageable.getSize(), totalElements, items);
            }

        } catch (SQLException e) {
            throw new SystemException(e);
        }
    }

    private String sqlToFindAssociations(RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable, boolean counting) {
        var sql = String.format("""
            select %s\s
            from ecm_associations a\s
            join ecm_nodes p on (a.parent_id = p.id)\s
            join ecm_nodes c on (a.child_id = c.id)\s
            where p.tenant = ? and c.tenant = p.tenant
            """,
            counting
                ? "count(*)"
                : "a.id,a.is_hard,a.type_name,a.name,a.code,a.parent_id,p.uuid parent_uuid,a.child_id,c.uuid child_uuid"
            );

        var filterNode = filterNodeTypes != null && !filterNodeTypes.isEmpty();
        var filterNodeParent = filterNode && relationship != null && (Objects.equals(relationship, PARENT) || Objects.equals(relationship, SOURCE));
        var filterNodeChild = filterNode && relationship != null && (Objects.equals(relationship, CHILD) || Objects.equals(relationship, TARGET));

        if (relationship == null) {
            sql += " and (p.uuid = ? or c.uuid = ?)";
        } else {
            switch (relationship) {
                case PARENT:
                    sql += " and c.uuid = ? and a.is_hard is not null";
                    break;
                case CHILD:
                    sql += " and p.uuid = ? and a.is_hard is not null";
                    break;
                case SOURCE:
                    sql += " and c.uuid = ? and a.is_hard is null";
                    break;
                case TARGET:
                    sql += " and p.uuid = ? and a.is_hard is null";
                    break;
                default:
                    throw new BadRequestException("Unexpected relationship: " + relationship);
            }
        }

        if (filterAssociationTypes != null && !filterAssociationTypes.isEmpty()) {
            sql += " and a.type_name = any (?)";
        }

        if (filterNodeParent) {
            sql += " and p.type_name = any (?)";
        }
        if (filterNodeChild) {
            sql += " and c.type_name = any (?)";
        }

        if (!counting) {
            sql += " order by a.is_hard desc, a.id asc";

            if (pageable != null) {
                sql += " offset ? limit ?";
            }
        }

        return sql;
    }

    private void setParamsToFindAssociations(PreparedStatement stmt, ActiveNode node, RelationshipKind relationship, Collection<String> filterAssociationTypes, Collection<String> filterNodeTypes, Pageable pageable, boolean counting) throws SQLException {
        int c = 0;
        stmt.setString(++c, node.getTenant());
        stmt.setString(++c, node.getUuid());
        if (relationship == null) {
            stmt.setString(++c, node.getUuid());
        }

        if (filterAssociationTypes != null && !filterAssociationTypes.isEmpty()) {
            stmt.setArray(++c, stmt.getConnection().createArrayOf("VARCHAR", filterAssociationTypes.toArray(new String[0])));
        }

        if (filterNodeTypes != null && !filterNodeTypes.isEmpty()) {
            stmt.setArray(++c, stmt.getConnection().createArrayOf("VARCHAR", filterNodeTypes.toArray(new String[0])));
        }

        if (!counting && pageable != null) {
            stmt.setInt(++c, pageable.getPage() * pageable.getSize());
            stmt.setInt(++c, pageable.getSize());
        }
    }

    public Optional<Association> findAssociation(String firstUUID, String secondUUID) {
        return call(conn -> {
            var sql = """
                select a.id,a.child_id,c.uuid child_uuid,a.parent_id,p.uuid parent_uuid,a.is_hard,a.type_name,a.name,a.code\s
                from ecm_associations a\s
                join ecm_nodes c on (a.child_id = c.id and c.tenant = ?)\s
                join ecm_nodes p on (a.parent_id = p.id and p.tenant = c.tenant)\s
                where (p.uuid = ? and c.uuid = ?) or (p.uuid = ? and c.uuid = ?)
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                var tenant = UserContextManager.getTenant();
                stmt.setString(1, tenant);
                stmt.setString(2, firstUUID);
                stmt.setString(3, secondUUID);
                stmt.setString(4, secondUUID);
                stmt.setString(5, firstUUID);

                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var a = new Association();
                        a.setId(rs.getLong("id"));

                        var parent = new ActiveNode();
                        parent.setId(rs.getLong("parent_id"));
                        parent.setUuid(rs.getString("parent_uuid"));
                        parent.setTenant(tenant);
                        a.setParent(parent);

                        var child = new ActiveNode();
                        child.setId(rs.getLong("child_id"));
                        child.setUuid(rs.getString("child_uuid"));
                        child.setTenant(tenant);
                        a.setChild(child);

                        a.setHard(DBUtils.getBoolean(rs, "is_hard"));
                        a.setTypeName(rs.getString("type_name"));
                        a.setName(rs.getString("name"));
                        a.setCode(rs.getString("code"));

                        return Optional.of(a);
                    }

                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Association findAssociationsWithSameName(String destination, String name) {
        return call(conn -> {
            try (var stmt = conn.prepareStatement("""
                select a.id, a.type_name, a.name, a.is_hard, a.parent_id, a.child_id, a.code\s
                from ecm_associations a join ecm_nodes n on n.id = a.parent_id\s
                where a.name = ? and n.uuid = ?
                """)) {
                stmt.setString(1, name);
                stmt.setString(2, destination);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var result = new Association();
                        result.setName(rs.getString("name"));
                        return result;
                    }
                } catch (SQLException e) {
                    log.error("Cannot execute query");
                    throw new SystemException(e);
                }
            } catch (SQLException e) {
                log.error("Cannot prepare statement");
                throw new SystemException(e);
            }
            return null;
        });
    }

    public void renameAssociation(Association association, long txId, String name, AtomicLong counter) {
        call(conn -> {
            try {
                try (var stmt = conn.prepareStatement("update ecm_associations set name = ?, code = ? where id = ?")) {
                    stmt.setString(1, name);
                    stmt.setString(2, StringUtils.lowerCase(name));
                    stmt.setLong(3, association.getId());
                    if (stmt.executeUpdate() < 1) {
                        throw new RuntimeException("Association " + association.getId() + " not updated");
                    }
                } catch (SQLException e) {
                    throw new ConflictException("Cannot update association. Name duplicated: " + e.getMessage());
                }

                try (var stmt = conn.prepareStatement("""
                    update ecm_paths set tx = ?, file_path = func_path_rename(?::text, file_path, path_parts, ?::integer)\s
                    where path_parts @> ? and array_position(path_parts, ?::integer) = array_position(path_parts, ?::integer) - 1
                    """))
                {
                    var parts = new Long[2];
                    parts[0] = association.getParent().getId();
                    parts[1] = association.getChild().getId();

                    stmt.setLong(1, txId);
                    stmt.setString(2, name);
                    stmt.setLong(3, association.getChild().getId());
                    stmt.setArray(4, conn.createArrayOf("INTEGER", parts));
                    stmt.setLong(5, parts[0]);
                    stmt.setLong(6, parts[1]);

                    int n = stmt.executeUpdate();
                    log.debug("{} paths renamed", n);
                    if (n > 0 && !UserContextManager.getTenantData().map(TenantData::isNumericPathEnabled).orElse(false)) {
                        n = propagatePathsTransaction(conn, txId);
                        log.debug("{} nodes updated with tx from paths", n);
                        if (counter != null) {
                            counter.addAndGet(n);
                        }
                    }
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public boolean deleteAssociation(Association association, long txId, boolean archive, DeleteMode deleteMode, AtomicLong counter) {
        return call(conn -> {
            try {
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

                        try (var stmt = conn.prepareStatement("""
                            delete from ecm_paths where path_parts @> ?\s
                            and array_position(path_parts, ?::integer) = array_position(path_parts, ?::integer) - 1
                            """))
                        {
                            stmt.setArray(1, conn.createArrayOf("INTEGER", parts));
                            stmt.setLong(2, parts[0]);
                            stmt.setLong(3, parts[1]);
                            int n = stmt.executeUpdate();
                            log.debug("{} paths removed", n);
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

                        try (var stmt = conn.prepareStatement("delete from ecm_paths where path_parts @> ?"))
                        {
                            stmt.setArray(1, conn.createArrayOf("INTEGER", parts));
                            int n = stmt.executeUpdate();
                            log.debug("{} paths removed", n);
                        }
                    }
                } else {
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

                if (association.getParent() != null) {
                    if (archive && deleteMode == DeleteMode.DELETE) {
                        try (var stmt = conn.prepareStatement("""
                            insert into ecm_archived_associations (id, parent_id, child_id, type_name, name, is_hard)\s
                            select a.id, a.parent_id, a.child_id, a.type_name, a.name, a.is_hard\s
                            from ecm_associations a\s
                            where a.id = ?
                            """))
                        {
                            stmt.setLong(1, association.getId());
                            if (stmt.executeUpdate() > 0) {
                                log.debug("Association {} copied into archive", association.getId());
                            } else {
                                log.warn("Association {} not copied into archive", association.getId());
                            }
                        } catch (SQLException e) {
                            log.error("Cannot copy main archived association {}: {}", association.getId(), e.getMessage());
                            throw e;
                        }
                    } // end if archive && DELETE

                    try (var stmt = conn.prepareStatement("delete from ecm_associations where id = ?")) {
                        stmt.setLong(1, association.getId());
                        if (stmt.executeUpdate() == 0) {
                            return false;
                        }
                    }
                }

                if (archive) {
                    if (deleteMode == DeleteMode.DELETE) {
                        try (var stmt = conn.prepareStatement("""
                            update ecm_nodes\s
                            set data = jsonb_set(data, '{aspects}', data->'aspects' || '"sys:archived"')\s
                            where id = ?
                            """))
                        {
                            stmt.setLong(1, association.getChild().getId());
                            int n = stmt.executeUpdate();
                            log.debug("{} aspect 'sys:archived' added", n);
                        } catch (SQLException e) {
                            log.error("Cannot insert aspect 'sys:archived'");
                            throw e;
                        }

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
                            """))
                        {
                            stmt.setString(1, UserContextManager.getContext().getAuthorityRef().toString());
                            stmt.setLong(2, txId);
                            int n = stmt.executeUpdate();
                            if (n > deleteLimit) {
                                throw new LimitExceededException("Delete limit exceeded: " + deleteLimit);
                            }
                            log.debug("{} archived nodes inserted", n);
                        } catch (SQLException e) {
                            log.error("Cannot insert archived nodes");
                            throw e;
                        }

                        var archivedNodesArray = listArchivedNodes(conn, txId);
                        try (var stmt = conn.prepareStatement("""
                            insert into ecm_archived_associations (id,parent_id,child_id,type_name,name,is_hard)\s
                            select a.id, a.parent_id, a.child_id, a.type_name, a.name, a.is_hard\s
                            from ecm_associations a\s
                            where a.child_id = any (?)\s
                            or a.parent_id = any (?)
                            """))
                        {
                            stmt.setArray(1, conn.createArrayOf("INTEGER", archivedNodesArray));
                            stmt.setArray(2, conn.createArrayOf("INTEGER", archivedNodesArray));
                            int n = stmt.executeUpdate();
                            log.debug("{} archived associations", n);
                        } catch (SQLException e) {
                            log.error("Cannot insert archived associations");
                            throw e;
                        }

                        deletePaths(conn, archivedNodesArray);
                        deleteAssociations(conn, archivedNodesArray);
                        deleteNodes(conn, archivedNodesArray);
                    } else { // PURGE
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
                            if (n > deleteLimit) {
                                throw new LimitExceededException("Delete limit exceeded: " + deleteLimit);
                            }
                            log.debug("{} nodes removed", n);
                        } catch (SQLException e) {
                            log.error("Cannot insert removed nodes");
                            throw e;
                        }

                        var archivedNodesArray = listPurgedNodes(conn, txId);
                        deletePaths(conn, archivedNodesArray);
                        deleteAssociations(conn, archivedNodesArray);
                        purgeNodeVersions(conn, archivedNodesArray);
                        deleteNodes(conn, archivedNodesArray);
                        cleanSecurityGroups(conn, txId);
                    }
                }

                return true;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private void deletePaths(Connection conn, Long[] archivedNodesArray) throws SQLException {
        try (var stmt = conn.prepareStatement("delete from ecm_paths where path_parts && ?"))
        {
            stmt.setArray(1, conn.createArrayOf("INTEGER", archivedNodesArray));
            int n = stmt.executeUpdate();
            log.debug("{} soft remaining paths deleted", n);
        } catch (SQLException e) {
            log.error("Cannot delete remaining paths");
            throw e;
        }
    }

    private void deleteAssociations(Connection conn, Long[] archivedNodesArray) throws SQLException {
        try (var stmt = conn.prepareStatement("""
                            delete from ecm_associations\s
                            where child_id = any (?)\s
                            or parent_id = any (?)
                            """))
        {
            stmt.setArray(1, conn.createArrayOf("INTEGER", archivedNodesArray));
            stmt.setArray(2, conn.createArrayOf("INTEGER", archivedNodesArray));
            int n = stmt.executeUpdate();
            log.debug("{} associations deleted", n);
        } catch (SQLException e) {
            log.error("Cannot delete associations");
            throw e;
        }
    }

    private void deleteNodes(Connection conn, Long[] archivedNodesArray) throws SQLException {
        if (deleteDeferred) {
            try (var stmt = conn.prepareStatement("""
                            update ecm_nodes\s
                            set data = jsonb_set(data, '{aspects}', data->'aspects' || '"ecm-sys:deleted"')\s
                            where id = any (?)
                            """))
            {
                stmt.setArray(1, conn.createArrayOf("INTEGER", archivedNodesArray));
                int n = stmt.executeUpdate();
                log.debug("{} aspect 'ecm-sys:deleted' added", n);
            } catch (SQLException e) {
                log.error("Cannot insert aspect 'ecm-sys:deleted'");
                throw e;
            }
        } else {
            try (var stmt = conn.prepareStatement("delete from ecm_nodes where id = any (?)"))
            {
                stmt.setArray(1, conn.createArrayOf("INTEGER", archivedNodesArray));
                int n = stmt.executeUpdate();
                log.debug("{} nodes deleted", n);
            } catch (SQLException e) {
                log.error("Cannot delete nodes");
                throw e;
            }
        }
    }

    public void createRootPath(ActiveNode rootNode) {
        call(conn -> {
            var sql = """
                insert into ecm_paths (node_id,lev,is_hard,node_path,file_path,sg_path,path_parts)
                select\s
                  n.node_id,
                  1 as lev,
                  true as is_hard,
                  ':' || n.node_id || ':' as node_path,
                  '/' as file_path,
                  ':' || n.node_id || ':' as sg_path,
                  func_path_parts(':' || n.node_id || ':') as path_parts\s
                from (select ? as node_id) n
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, rootNode.getId());
                if (stmt.executeUpdate() < 1) {
                    throw new SQLException("No root path created");
                }
                return null;
            } catch (SQLException e) {
                log.error("Unable to create root path: {}", e.getMessage());
                throw new SystemException(e);
            }
        });
    }

    public void createAssociation(ApplicationTransaction tx, Association association, AtomicLong counter) {
        Optional<Long> previousParent = association.getChild().getParents().stream()
            .map(Association::getParent)
            .map(ActiveNode::getId).findFirst();

        call(conn -> {
            var sql = """
                insert into ecm_associations\s
                (parent_id, child_id, type_name, name, code, is_hard)\s
                values (?,?,?,?,?,?)\s
                on conflict (parent_id, code) do nothing
                """;
            try (var stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, association.getParent().getId());
                stmt.setLong(2, association.getChild().getId());
                stmt.setString(3, association.getTypeName());
                stmt.setString(4, association.getName());
                stmt.setString(5, association.getCode());
                stmt.setObject(6, association.getHard());

                if (stmt.executeUpdate() < 1) {
                    var conflictException = new ConflictException(String.format("Duplicate node association. Node %s already has a child with the name '%s'", association.getParent().getUuid(), association.getCode()));
                    var duplicateSQL = """
                            select n.uuid from ecm_associations a\s
                            join ecm_nodes n on a.child_id = n.id\s
                            where a.parent_id = ? and a.code = ?
                            """;
                    try (var duplicateStmt = conn.prepareStatement(duplicateSQL)) {
                        duplicateStmt.setLong(1, association.getParent().getId());
                        duplicateStmt.setString(2, association.getCode());
                        try (var rs = duplicateStmt.executeQuery()) {
                            if (rs.next()) {
                                conflictException.getDetailMap().put("currentUUID", rs.getString("uuid"));
                            }
                        }
                    } catch (SQLException e) {
                        log.error("SQLException finding duplicate node {}: {}", e.getErrorCode(), e.getMessage());
                    }

                    throw conflictException;
                }

                try (var generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        association.setId(generatedKeys.getLong(1));
                    } else {
                        throw new RuntimeException("Creating association failed, no ID obtained.");
                    }
                }

                log.debug("Association {} created", association.getId());
            } catch (SQLException e) {
                throw new PreconditionFailedException(String.format("Unable to create association between node %s and a child with the name '%s'", association.getParent().getUuid(), association.getCode()));
            }

            if (association.getHard() != null) {
                int n = createPaths(conn, association);
                log.debug("{} paths created for node {}", n, association.getChild().getId());
                previousParent.ifPresent(id -> {
                    int nn = addPaths(conn, tx, association, id);
                    log.debug("{} paths added for descendents of node {}", nn, association.getChild().getId());
                    if (nn > 0) {
                        nn = propagatePathsTransaction(conn, tx.getId());
                        log.debug("{} nodes updated with tx from paths", nn);
                        if (counter != null) {
                            counter.addAndGet(nn);
                        }
                    }
                });
            } else {
                try (var stmt = conn.prepareStatement("update ecm_nodes set tx = ? where id = ?")) {
                    stmt.setLong(1, tx.getId());
                    stmt.setLong(2, association.getChild().getId());
                    int n = stmt.executeUpdate();
                    log.debug("{} nodes updated with tx {}", n, tx.getId());
                    if (counter != null) {
                        counter.addAndGet(n);
                    }
                } catch (SQLException e) {
                    log.error("Cannot set transaction {} on node {}", tx.getId(), association.getChild().getId());
                    throw new SystemException(e);
                }
            }

            return null;
        });
    }

    private int createPaths(Connection conn, Association association) {
        var sql = """
            insert into ecm_paths (node_id,lev,is_hard,node_path,file_path,sg_path,parent_id,path_parts)
            select
                ? as node_id,
                p.lev + 1 as lev,
                p.is_hard and ? as is_hard,
                p.node_path || ? || ':' as node_path,
                p.file_path || ? || '/' as file_path,
                case when ? then coalesce(p.sg_path, ':' || p.node_id || ':') || ? || ':' else ':' || ? || ':' end as sg_path,
                p.node_id as parent_id,
                func_path_parts(p.node_path || ? || ':') as path_parts\s
            from ecm_paths p\s
            where node_id = ?
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            var childId = association.getChild().getId();
            var inheritance = Optional.ofNullable(association.getChild().getSecurityGroup())
                .map(SecurityGroup::isInheritanceEnabled)
                .orElse(false);

            stmt.setLong(1, childId);
            stmt.setBoolean(2, association.isHard());
            stmt.setLong(3, childId);
            stmt.setString(4, association.getName());
            stmt.setBoolean(5, inheritance);
            stmt.setLong(6, childId);
            stmt.setLong(7, childId);
            stmt.setLong(8, childId);
            stmt.setLong(9, association.getParent().getId());

            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int addPaths(Connection conn, ApplicationTransaction tx, Association association, long prevParentId) {
        var sql = """
            insert into ecm_paths (node_id,parent_id,node_path,path_parts,lev,is_hard,file_path,sg_path,tx)\s
            select\s
                p.node_id,
                p.parent_id,
                x.node_path,
                func_path_parts(x.node_path),
                cardinality(string_to_array(x.node_path, ':')) - 2 as lev,
                p.is_hard and q.is_hard and x.is_hard as is_hard,
                func_path_combine(q.file_path, p.file_path, p.node_path, x.node_id::text, ?) as file_path,
                case when position(':' || x.node_id::text || ':' in p.sg_path) > 0 and x.inheritance\s
                    then q.sg_path || substr(p.sg_path, strpos(p.sg_path, ':' || x.node_id::text || ':') + 1)\s
                    else x.sg_path\s
                end as sg_path,
                ? as tx
            from ecm_paths p, ecm_paths q\s
            join lateral (
                select\s
                    ? as node_id,
                    ? as is_hard,
                    ? as inheritance,
                    q.node_path || substr(p.node_path, strpos(p.node_path, ':' || ?::text || ':') + 1) as node_path,
                    coalesce(p.sg_path, ':' || p.node_id || ':') as sg_path
            ) as x on true\s
            where p.path_parts @> ? and p.node_id != x.node_id\s
            and array_position(p.path_parts, ?::integer) = array_position(p.path_parts, ?::integer) - 1\s
            and q.node_id = ?\s
            on conflict (node_path) do nothing
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, association.getName());
            stmt.setLong(2, tx.getId());
            stmt.setLong(3, association.getChild().getId());
            stmt.setBoolean(4, association.isHard());
            stmt.setBoolean(5, Optional.ofNullable(association.getChild().getSecurityGroup()).map(SecurityGroup::isInheritanceEnabled).orElse(false));
            stmt.setLong(6, association.getChild().getId());

            var parts = new Long[2];
            parts[0] = prevParentId;
            parts[1] = association.getChild().getId();
            stmt.setArray(7, conn.createArrayOf("INTEGER", parts));
            stmt.setLong(8, parts[0]);
            stmt.setLong(9, parts[1]);
            stmt.setLong(10, association.getParent().getId());

            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int propagatePathsTransaction(Connection conn, long txId) {
        var sql = """
                update ecm_nodes set tx = ?, tx_flags = ?\s
                where id in (select node_id from ecm_paths where tx = ?)
                """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, txId);
            stmt.setString(2, formatAsBinary(PATH_FLAG));
            stmt.setLong(3, txId);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

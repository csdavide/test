package it.doqui.libra.librabl.business.provider.data.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.data.entities.*;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.association.LinkItem;
import it.doqui.libra.librabl.views.association.LinkMode;
import it.doqui.libra.librabl.views.association.RelationshipKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.FULL_FLAG_MASK;
import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.formatAsBinary;

@ApplicationScoped
@Slf4j
public class ArchiveDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    public Optional<ArchivedNode> getNode(String uuid) {
        return getNodeWhere("n.tenant = ? and n.uuid = ?", stmt -> {
            try {
                int c = 0;
                stmt.setString(++c, UserContextManager.getContext().getTenantRef().toString());
                stmt.setString(++c, uuid);
                return c;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<ArchivedNode> getNode(long id) {
        return getNodeWhere("n.id = ?", stmt -> {
            try {
                int c = 0;
                stmt.setLong(++c, id);
                return c;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private Optional<ArchivedNode> getNodeWhere(String where, Function<PreparedStatement,Integer> setParamsFunc) {
        return call(conn -> {
            var sql = """
                select n.id,n.tenant,n.uuid,n.type_name,n.data,n.updated_at,n.tx\s
                from ecm_archived_nodes n where\s
                """;

            sql += where;

            if (!UserContextManager.getContext().isAdmin()) {
                sql += """
                     and (\s
                       n.sg_id is null or n.sg_id in (\s
                         select r.sg_id from ecm_access_rules r\s
                         where r.rights like '1%' and r.authority in (
                           select g.groupname from ecm_groups g\s
                           join ecm_user_groups ug on ug.group_id = g.id\s
                           join ecm_users u on ug.user_id = u.id\s
                           where u.username = ?\s
                           union\s
                           select ?
                         )
                       )
                    )
                    """;
            }

            try (var stmt = conn.prepareStatement(sql)) {
                int c = setParamsFunc.apply(stmt);
                if (!UserContextManager.getContext().isAdmin()) {
                    String username = UserContextManager.getContext().getAuthority();
                    stmt.setString(++c, username);
                    stmt.setString(++c, username);
                }

                try (var rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    var n = readArchivedNode(rs, true);
                    return Optional.of(n);
                }
            } catch (SQLException | JsonProcessingException e) {
                throw new SystemException(e);
            }
        });
    }

    private ArchivedNode readArchivedNode(ResultSet rs, boolean includeMetadata) throws SQLException, JsonProcessingException {
        var n = new ArchivedNode();
        n.setId(rs.getLong("id"));
        n.setTenant(rs.getString("tenant"));
        n.setUuid(rs.getString("uuid"));
        n.setTypeName(rs.getString("type_name"));

        if (includeMetadata) {
            var json = rs.getString("data");
            if (json != null) {
                var data = objectMapper.readValue(json, NodeData.class);
                n.getData().copyFrom(data);
            }

            n.setUpdatedAt(DBUtils.getZonedDateTime(rs, "updated_at"));

            var tx = new ApplicationTransaction();
            tx.setId(rs.getLong("tx"));
            n.setTx(tx);
        }

        return n;
    }

    public List<ArchivedAssociation> findParentAssociations(long nodeId, boolean hardOnly) {
        return call(conn -> {
            var sql = """
                select a.id,
                a.parent_id,
                active.uuid as active_parent_uuid,
                archived.uuid as archived_parent_uuid,
                child.uuid as child_uuid,
                a.type_name,a.name,a.code,a.is_hard\s
                from ecm_archived_associations a join ecm_archived_nodes child on a.child_id = child.id\s
                left outer join ecm_archived_nodes archived on archived.id = a.parent_id\s
                left outer join ecm_nodes active on active.id = a.parent_id\s
                where a.child_id = ? and case when ? then a.is_hard else true end\s
                order by a.id
                """;

            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, nodeId);
                stmt.setBoolean(2, hardOnly);
                try (var rs = stmt.executeQuery()) {
                    var associations = new ArrayList<ArchivedAssociation>();
                    while (rs.next()) {
                        var a = new ArchivedAssociation();
                        a.setId(rs.getLong("id"));
                        a.setActiveParentUuid(rs.getString("active_parent_uuid"));
                        a.setArchivedParentUuid(rs.getString("archived_parent_uuid"));
                        a.setParentId(rs.getLong("parent_id"));
                        a.setChildId(nodeId);
                        a.setChildUuid(rs.getString("child_uuid"));
                        a.setTypeName(rs.getString("type_name"));
                        a.setName(rs.getString("name"));
                        a.setCode(rs.getString("code"));
                        a.setHard(rs.getBoolean("is_hard"));
                        associations.add(a);
                    }
                    return associations;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<LinkItem> findRestorableParentAssociation(long nodeId) {
        return call(conn -> {
            var sql = """
                select a.type_name,a.name,n.uuid\s
                from ecm_archived_associations a\s
                join ecm_nodes n on (a.parent_id = n.id)\s
                where a.child_id = ? and a.is_hard\s
                order by a.id\s
                limit 1
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, nodeId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var link = new LinkItem();
                        link.setTypeName(rs.getString("type_name"));
                        link.setName(rs.getString("name"));
                        link.setVertexUUID(rs.getString("uuid"));
                        link.setHard(true);
                        link.setRelationship(RelationshipKind.PARENT);

                        return Optional.of(link);
                    }

                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Paged<ArchivedNode> findNodes(Collection<String> uuid, Collection<String> types, Collection<String> aspects, boolean includeMetadata, boolean excludeDescendants, Pageable pageable) {
        return call(conn -> findNodes(conn, uuid, types, aspects, includeMetadata, excludeDescendants, pageable));
    }

    private Paged<ArchivedNode> findNodes(Connection conn, Collection<String> uuid, Collection<String> types, Collection<String> aspects, boolean includeMetadata, boolean excludeDescendants, Pageable pageable) {
        String sql = "select {fields} from ecm_archived_nodes n where n.tenant = ?";

        if (uuid != null && !uuid.isEmpty()) {
            sql += " and n.uuid = any (?) ";
        }

        if (types != null && !types.isEmpty()) {
            sql += " and n.type_name = any (?)";
        }

        if (excludeDescendants) {
            if (aspects == null) {
                aspects = new ArrayList<>();
            }
            aspects.add(Constants.ASPECT_SYS_ARCHIVED);
        }

        if (aspects != null && !aspects.isEmpty()) {
            sql += " and n.data->'aspects' @> ?::jsonb";
        }

        if (!UserContextManager.getContext().isAdmin()) {
            sql += " and (n.sg_id is null or n.sg_id in (select r.sg_id from ecm_access_rules r" +
                " where r.rights like '1%' " +
                " and r.authority in (" +
                "    select g.groupname " +
                "    from ecm_groups g " +
                "    join ecm_user_groups ug on ug.group_id = g.id " +
                "    join ecm_users u on ug.user_id = u.id " +
                "    where u.username = ? " +
                "    union " +
                "    select ? " +
                ")))";
        }

        long totalElements = -1;
        if (pageable != null && pageable.getSize() > 0) {
            try (PreparedStatement stmt = conn.prepareStatement(sql.replace("{fields}", "count(*)"))) {
                setParams(stmt, 0, uuid, types, aspects);
                try (ResultSet rs = stmt.executeQuery()) {
                    totalElements = rs.next() ? rs.getLong(1) : 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            sql += " order by n.id limit ? offset ?";
        }

        String fields = includeMetadata ? "n.id,n.tenant,n.uuid,n.type_name,n.data,n.updated_at,n.tx" : "n.id,n.tenant,n.uuid,n.type_name";
        try (PreparedStatement stmt = conn.prepareStatement(sql.replace("{fields}", fields))) {
            int c = setParams(stmt, 0, uuid, types, aspects);
            if (pageable != null) {
                stmt.setInt(++c, pageable.getSize());
                stmt.setInt(++c, pageable.getPage() * pageable.getSize());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<ArchivedNode> nodes = new ArrayList<>();
                while (rs.next()) {
                    nodes.add(readArchivedNode(rs, includeMetadata));
                } // end while

                final Paged<ArchivedNode> result;
                if (pageable == null || pageable.getSize() <= 0) {
                    result = new Paged<>(nodes);
                } else {
                    long totalPages = -1;
                    if (totalElements == 0) {
                        totalPages = 0;
                    } else if (totalElements > 0) {
                        totalPages = totalElements / pageable.getSize() + (totalElements % pageable.getSize() == 0 ? 0 : 1);
                    }

                    result = new Paged<>(pageable.getPage(), pageable.getSize(), totalElements, totalPages, nodes);
                }

                return result;
            }
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    private int setParams(PreparedStatement stmt, int c, Collection<String> uuid, Collection<String> types, Collection<String> aspects) throws SQLException {
        stmt.setString(++c, UserContextManager.getContext().getTenantRef().toString());

        if (uuid != null && !uuid.isEmpty()) {
            stmt.setArray(++c, stmt.getConnection().createArrayOf("VARCHAR", uuid.toArray(new String[0])));
        }

        if (types != null && !types.isEmpty()) {
            stmt.setArray(++c, stmt.getConnection().createArrayOf("VARCHAR", types.toArray(new String[0])));
        }

        if (aspects != null && !aspects.isEmpty()) {
            String a = aspects
                .stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(",", "[", "]"));
            stmt.setString(++c, a);
        }

        if (!UserContextManager.getContext().isAdmin()) {
            String username = UserContextManager.getContext().getAuthority();
            stmt.setString(++c, username);
            stmt.setString(++c, username);
        }

        return c;
    }

    public void deleteArchivedAssociations(
        List<ArchivedAssociation> archivedAssociations, long oldTx, long newTx, long rootId, boolean isClosureRoot,
        boolean remove, AtomicLong counter) {
        call(conn -> {
            try {
                //delete main archived associations
                try (var stmt = conn.prepareStatement("delete from ecm_archived_associations where id = any(?)")) {
                    stmt.setArray(1, conn.createArrayOf("INTEGER", archivedAssociations.stream().map(ArchivedAssociation::getId).toList().toArray(new Long[0])));
                    int n = stmt.executeUpdate();
                    log.debug("{} main archived association{} deleted", n,  (n == 1 ? "" : "s"));
                } catch (SQLException e) {
                    log.error("Cannot delete main archived associations");
                    throw e;
                }

                int n = purgeUnreachableNodes(conn, oldTx, newTx, rootId, isClosureRoot, remove);
                if (counter != null) {
                    counter.addAndGet(n);
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private int purgeUnreachableNodes(Connection conn, long oldTx, long newTx, long rootId, boolean isClosureRoot, boolean wipeable) throws SQLException {
        var nodes = 0;
        //1. create new removed nodes from archived nodes
        if (isClosureRoot) {
            try (var stmt = conn.prepareStatement("""
                        insert into ecm_removed_nodes (id, tenant, uuid, tx, data, sg_id, wipeable)\s
                        select id, tenant, uuid, ?,
                        case when jsonb_exists(data, 'contents') then jsonb_build_object('contents', data->'contents') end,
                        sg_id, ?\s
                        from ecm_archived_nodes\s
                        where tx = ?
                        """))
            {
                stmt.setLong(1, newTx);
                stmt.setBoolean(2, wipeable);
                stmt.setLong(3, oldTx);
                var n = stmt.executeUpdate();
                log.debug("{} removed nodes inserted", n);
            } catch (SQLException e) {
                log.error("Cannot insert nodes into removed table");
                throw e;
            }
        } else {
            //insert into removed nodes only the nodes whose path has to be rebuilt
            insertRemovedNodesWhereTx(conn, oldTx, newTx, rootId, wipeable);
        }

        var purgedNodesArray = listPurgedNodes(conn, newTx);
        //2. delete archived associations
        try (var stmt = conn.prepareStatement("""
                    delete from ecm_archived_associations a\s
                    where a.parent_id = any(?)\s
                    or a.child_id = any(?)
                    """))
        {
            stmt.setArray(1, conn.createArrayOf("INTEGER", purgedNodesArray));
            stmt.setArray(2, conn.createArrayOf("INTEGER", purgedNodesArray));
            var n = stmt.executeUpdate();
            log.debug("{} archived associations deleted", n);
        } catch (SQLException e) {
            log.error("Cannot delete archived associations");
            throw e;
        }

        //3. delete archived nodes
        try (var stmt = conn.prepareStatement("""
                    delete from ecm_archived_nodes\s
                    where id = any(?)
                    """)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", purgedNodesArray));
            nodes = stmt.executeUpdate();
            log.debug("{} archived nodes deleted", nodes);
        } catch (SQLException e) {
            log.error("Cannot delete archived nodes");
            throw e;
        }

        purgeNodeVersions(conn, newTx);
        cleanSecurityGroups(conn, newTx);
        return nodes;
    }

    private void insertRemovedNodesWhereTx(Connection conn, long oldTx, long newTx, long rootId, boolean wipeable) throws SQLException {
        try (var stmt = conn.prepareStatement("""
                    with recursive build_paths (node_id, node_path, is_hard, root_id, parent_id) as (\s
                        select n.id,
                            ':' || n.id || ':',
                            a.is_hard,
                            a.parent_id,
                            a.parent_id\s
                        from ecm_archived_nodes n\s
                        join ecm_archived_associations a on (n.id = a.child_id and a.is_hard is not null)\s
                        where n.tx = ?\s
                    union\s
                        select b.node_id,
                            ':' || n.id || b.node_path,
                            a.is_hard and b.is_hard,
                            a.parent_id,
                            b.parent_id\s
                        from build_paths b\s
                        join ecm_archived_nodes n on n.id = b.root_id\s
                        join ecm_archived_associations a on (a.child_id = b.root_id and a.is_hard is not null)\s
                    )\s
                    insert into ecm_removed_nodes (id, data, tenant, uuid, tx, wipeable)\s
                    select n.id, jsonb_build_object('contents', n.data->'contents'), n.tenant, n.uuid, ?, ?\s
                    from ecm_archived_nodes n\s
                    where n.tx = ? and n.id in (select node_id\s
                        from build_paths\s
                        where root_id = ? and is_hard\s
                    ) or n.id = ?
                    """))
        {
            stmt.setLong(1, oldTx);
            stmt.setLong(2, newTx);
            stmt.setBoolean(3, wipeable);
            stmt.setLong(4, oldTx);
            stmt.setLong(5, rootId);
            stmt.setLong(6, rootId);
            var n = stmt.executeUpdate();
            log.debug("{} temporary archived paths built", n);
        } catch (SQLException e) {
            log.debug("Cannot rebuild archived paths to insert removed nodes");
            throw e;
        }
    }

    public void restoreTx(ArchivedNode node, long newTx, AtomicLong counter) {
        call(conn -> {
            try {
                var sql = """
                insert into ecm_nodes (id, version, data, tenant, type_name, updated_at, uuid, sg_id, tx, tx_flags)\s
                select id, version,
                  jsonb_set(data, '{aspects}', (data::jsonb #> '{aspects}') - 'sys:archived')\s
                  #- '{"properties", "sys:archivedBy"}'\s
                  #- '{"properties", "sys:archivedDate"}',
                  tenant, type_name, now(), uuid, sg_id, ?, ?\s
                from ecm_archived_nodes\s
                where tx = ?
                """;

                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, newTx);
                    stmt.setString(2, formatAsBinary(FULL_FLAG_MASK));
                    stmt.setLong(3, node.getTx().getId());
                    int n =  stmt.executeUpdate();
                    log.debug("{} nodes restored into tx {}", n, newTx);
                    if (counter != null) {
                        counter.addAndGet(n);
                    }
                } catch (SQLException e) {
                    log.error("Error restoring nodes in tx {}: {}", node.getTx().getId(), e.getMessage());
                    throw e;
                }

                sql = """
                    insert into ecm_associations (id, is_hard, name, type_name, child_id, parent_id, code)\s
                    select a.id, a.is_hard, a.name, a.type_name, a.child_id, a.parent_id, a.code\s
                    from ecm_archived_associations a\s
                    join ecm_nodes p on (p.id = a.parent_id)\s
                    join ecm_nodes c on (c.id = a.child_id)\s
                    where c.tx = ? and (c.id != ? or a.is_hard is null)
                    """;
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, newTx);
                    stmt.setLong(2, node.getId());
                    int n =  stmt.executeUpdate();
                    log.debug("{} associations restored into tx {}", n, newTx);
                } catch (SQLException e) {
                    log.error("Error restoring subtree associations of tx {}: {}", node.getTx().getId(), e.getMessage());
                    throw e;
                }

                sql = """
                    insert into ecm_associations (id, is_hard, name, type_name, child_id, parent_id, code)\s
                    select a.id, a.is_hard, a.name, a.type_name, a.child_id, a.parent_id, a.code\s
                    from ecm_archived_associations a\s
                    join ecm_nodes p on (p.id = a.parent_id)\s
                    join ecm_nodes c on (c.id = a.child_id)\s
                    where p.tx = ? and c.tx != p.tx and a.is_hard is null
                    """;
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, newTx);
                    int n =  stmt.executeUpdate();
                    log.debug("{} source/target associations restored into tx {}", n, newTx);
                } catch (SQLException e) {
                    log.error("Error restoring source/target associations of tx {}: {}", node.getTx().getId(), e.getMessage());
                    throw e;
                }

                restoreEmbeddedAssociations(conn, "n.data->'properties'->>'sys:archivedSourceAssocs'", false, node.getTx().getId(), null);
                restoreEmbeddedAssociations(conn, "n.data->'properties'->>'sys:archivedTargetAssocs'", true, node.getTx().getId(), null);
                restoreEmbeddedAssociations(conn, "n.data->'properties'->>'sys:archivedParentAssocs'", false, node.getTx().getId(), null);
                restoreEmbeddedAssociations(conn, "n.data->'properties'->>'sys:archivedChildAssocs'", true, node.getTx().getId(), newTx);

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private void restoreEmbeddedAssociations(Connection conn, String field, boolean isChild, long oldTx, Long newTx) throws SQLException {
        var sql = String.format("""
                insert into ecm_associations (is_hard, name, type_name, child_id, parent_id)\s
                select %s, z.name, z.type, z.child, z.parent\s
                from (
                select jsonb_array_elements((%s)::jsonb) as link\s
                from ecm_archived_nodes n\s
                where n.tx = ? and (%s)::jsonb is not null
                ) as x,\s
                jsonb_to_record(x.link) as z (hard boolean, name varchar, type varchar, child int8, parent int8)\s
                join ecm_nodes m on (%s = m.id%s)\s
                on conflict (parent_id,child_id) do nothing
                """,
            StringUtils.contains(field, "Source") || StringUtils.contains(field, "Target") ? "null" : "z.hard",
            field, field, isChild ? "z.child" : "z.parent", newTx == null ? "" : " and m.tx = ?");

        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, oldTx);
            if (newTx != null) {
                stmt.setLong(2, newTx);
            }

            int n = stmt.executeUpdate();
            log.debug("{} embedded associations created", n);
        }
    }

    public void restoreFirstNodeAssociations(ArchivedNode node, LinkMode mode) {
        call(conn -> {
            try {
                var sql = """
                    insert into ecm_associations (id, is_hard, name, type_name, child_id, parent_id, code)\s
                    select a.id, a.is_hard, a.name, a.type_name, a.child_id, a.parent_id, a.code\s
                    from ecm_archived_associations a\s
                    join ecm_nodes p on (p.id = a.parent_id)\s
                    join ecm_nodes c on (c.id = a.child_id)\s
                    where c.id = ? and a.is_hard is not null and ((a.is_hard and ?) or ?)
                    order by a.id
                    limit ?
                    """;
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, node.getId());
                    stmt.setBoolean(2, mode == LinkMode.HARD || mode == LinkMode.FIRST);
                    stmt.setBoolean(3, mode == LinkMode.ALL);
                    stmt.setInt(4, mode == LinkMode.FIRST ? 1 : Integer.MAX_VALUE);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    log.error("Error restoring closure root incoming associations");
                    throw e;
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void retrieveOutgoingEmbeddedAssociations(long oldTx, long newTx, Consumer<Association> consumer) {
        call(conn -> {
            try {
                var sql = """
                    select z.hard, z.name, z.type, z.child, z.parent, m.uuid parent_uuid, m.uuid child_uuid\s
                    from (
                    select jsonb_array_elements(n.data->'properties'->'sys:archivedChildAssocs') as link\s
                    from ecm_archived_nodes n\s
                    where n.tx = ? and n.data->'properties'->'sys:archivedChildAssocs' is not null\s
                    ) as x,
                    jsonb_to_record(x.link) as z (hard boolean, name varchar, type varchar, child int8, parent int8)\s
                    join ecm_nodes m on (z.child = m.id and m.tx != ?)
                    """;
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, oldTx);
                    stmt.setLong(2, newTx);
                    try (var rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            var association = new Association();
                            association.setHard(DBUtils.getBoolean(rs, "hard"));
                            association.setTypeName(rs.getString("type"));
                            association.setName(rs.getString("name"));

                            var child = new ActiveNode();
                            child.setId(rs.getLong("child"));
                            child.setUuid(rs.getString("child_uuid"));
                            association.setChild(child);

                            var parent = new ActiveNode();
                            parent.setId(rs.getLong("parent"));
                            parent.setUuid(rs.getString("parent_uuid"));
                            association.setParent(parent);

                            if (consumer != null) {
                                consumer.accept(association);
                            }
                        }
                    }
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void retrieveOutgoingAssociations(long newTx, Consumer<Association> consumer) {
        call(conn -> {
            try {
                var sql = """
                    select a.id, a.is_hard, a.name, a.type_name, a.child_id, a.parent_id, a.code,
                    p.uuid parent_uuid, c.uuid child_uuid\s
                    from ecm_archived_associations a\s
                    join ecm_nodes p on (p.id = a.parent_id)\s
                    join ecm_nodes c on (c.id = a.child_id)\s
                    where p.tx = ? and c.tx != p.tx and a.is_hard is not null
                    """;
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, newTx);
                    try (var rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            var association = new Association();
                            association.setId(rs.getLong("id"));
                            association.setHard(DBUtils.getBoolean(rs, "is_hard"));
                            association.setTypeName(rs.getString("type_name"));
                            association.setName(rs.getString("name"));
                            association.setCode(rs.getString("code"));

                            var child = new ActiveNode();
                            child.setId(rs.getLong("child_id"));
                            child.setUuid(rs.getString("child_uuid"));
                            association.setChild(child);

                            var parent = new ActiveNode();
                            parent.setId(rs.getLong("parent_id"));
                            parent.setUuid(rs.getString("parent_uuid"));
                            association.setParent(parent);

                            if (consumer != null) {
                                consumer.accept(association);
                            }
                        }
                    }
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public void deleteArchivedNodesAndAssociations(long oldTx) {
        call(conn -> {
            try {
                var sql = """
                    delete from ecm_archived_associations\s
                    where parent_id in (select id from ecm_archived_nodes where tx = ?)
                    or child_id in (select id from ecm_archived_nodes where tx = ?)
                    """;
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, oldTx);
                    stmt.setLong(2, oldTx);
                    int n = stmt.executeUpdate();
                    log.debug("{} archived associations deleted", n);
                }

                try (var stmt = conn.prepareStatement("delete from ecm_archived_nodes where tx = ?")) {
                    stmt.setLong(1, oldTx);
                    int n = stmt.executeUpdate();
                    log.debug("{} archived nodes deleted", n);
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }
}

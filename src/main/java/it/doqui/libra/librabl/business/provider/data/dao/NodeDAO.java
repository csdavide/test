package it.doqui.libra.librabl.business.provider.data.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.data.entities.*;
import it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.node.QueryScope;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.node.ContentProperty;
import it.doqui.libra.librabl.views.node.CopyMode;
import it.doqui.libra.librabl.views.node.MapOption;
import it.doqui.libra.librabl.views.node.NodeInfoItem;
import it.doqui.libra.librabl.views.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static it.doqui.libra.librabl.business.provider.integration.indexing.IndexingFlags.formatAsBinary;
import static it.doqui.libra.librabl.views.node.MapOption.*;

@ApplicationScoped
@Slf4j
public class NodeDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    public Optional<NodeInfoItem> findNodeIdWhereUUID(String uuid) {
        return call(conn -> {
            var sql = """
                select n.id,n.uuid,n.type_name\s
                from ecm_nodes n\s
                where n.tenant = ? and n.uuid = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                var tenant = UserContextManager.getTenant();
                stmt.setString(1, tenant);
                stmt.setString(2, uuid);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var n = new NodeInfoItem(
                            rs.getLong("id"),
                            tenant,
                            rs.getString("uuid"),
                            rs.getString("type_name")
                        );

                        return Optional.of(n);
                    }

                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public boolean setNodeProperty(ApplicationTransaction tx, String uuid, String propertyName, Object value) {
        return call(conn -> {
            var sql = """
                update ecm_nodes set tx = ?, version = version + 1,\s
                data = jsonb_set(data, ?::text[], to_jsonb(?))\s
                where tenant = ? and uuid = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, tx.getId());
                stmt.setString(2, String.format("{properties, %s}", propertyName));
                stmt.setObject(3, value);
                stmt.setString(4, UserContextManager.getContext().getTenantRef().toString());
                stmt.setString(5, uuid);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void createNode(ActiveNode node) {
        call(conn -> {
            var sql = """
                insert into ecm_nodes (tenant,uuid,type_name,sg_id,tx,tx_flags,data,updated_at)\s
                values (?,?,?,?,?,?,?::jsonb,?)
                """;
            try (var stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, node.getTenant());
                stmt.setString(2, node.getUuid());
                stmt.setString(3, node.getTypeName());
                DBUtils.setLong(stmt, 4, Optional.ofNullable(node.getSecurityGroup()).map(SecurityGroup::getId).orElse(null));
                DBUtils.setLong(stmt, 5, Optional.ofNullable(node.getTx()).map(ApplicationTransaction::getId).orElse(null));
                stmt.setString(6, node.getTransactionFlags());
                stmt.setString(7, objectMapper.writeValueAsString(node.getData()));
                stmt.setTimestamp(8, new Timestamp(System.currentTimeMillis()));

                if (stmt.executeUpdate() < 1) {
                    throw new RuntimeException("Unable to create node");
                }

                try (var generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        node.setId(generatedKeys.getLong(1));
                    } else {
                        throw new RuntimeException("Node creation failed, no ID obtained.");
                    }
                }

                log.debug("Node {} created", node.getId());
                return null;
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateNode(ActiveNode node) {
        call(conn -> {
            var sql = """
                update ecm_nodes set version = version + 1,\s
                type_name = ?, sg_id = ?, tx = ?, tx_flags = ?, data = ?::jsonb, updated_at = ?\s
                where id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, node.getTypeName());
                DBUtils.setLong(stmt, 2, Optional.ofNullable(node.getSecurityGroup()).map(SecurityGroup::getId).orElse(null));
                DBUtils.setLong(stmt, 3, Optional.ofNullable(node.getTx()).map(ApplicationTransaction::getId).orElse(null));
                stmt.setString(4, node.getTransactionFlags());
                stmt.setString(5, objectMapper.writeValueAsString(node.getData()));
                stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                stmt.setLong(7, node.getId());

                if (stmt.executeUpdate() < 1) {
                    throw new RuntimeException("Unable to update node");
                }

                log.debug("Node {} updated", node.getId());
                return null;
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void setTransaction(long nodeId, long txId, int flags) {
        call(conn -> {
            try (var stmt = conn.prepareStatement("update ecm_nodes set tx = ?, tx_flags = ? where id = ?")) {
                stmt.setLong(1, txId);
                stmt.setString(2, formatAsBinary(flags));
                stmt.setLong(3, nodeId);
                stmt.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void incrementContentRefCounterForTX(ApplicationTransaction tx) {
        call(conn -> {
            try (var stmt = conn.prepareStatement("""
                update ecm_files set counter = counter + s.cnt\s
                from (
                    select jsonb_array_elements(data->'contents')->>'contentUrl' as content, count(*) as cnt\s
                    from ecm_nodes where tx = ? and data->'contents' is not null\s
                    group by content
                ) s\s
                where tenant = ? and contentref = s.content and counter >= 0
                """))
            {
                stmt.setLong(1, tx.getId());
                stmt.setString(2, tx.getTenant());
                int n = stmt.executeUpdate();
                log.debug("{} file reference counters incremented", n);
                return null;
            } catch (SQLException e) {
                log.error("Error incrementing file reference counter: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    public void decrementContentRef(String tenant, String contentUrl) {
        var cp = new ContentProperty();
        cp.setContentUrl(contentUrl);
        decrementContentRef(tenant, List.of(cp));
    }

    public void decrementContentRef(ActiveNode node) {
        decrementContentRef(node.getTenant(), node.getData().getContents());
    }

    public void decrementContentRef(String tenant, List<ContentProperty> contents) {
        if (contents != null && !contents.isEmpty()) {
            call(conn -> {
                try (var stmt = conn.prepareStatement("update ecm_files set counter = counter - 1 where tenant = ? and contentref = ? and counter > 0")) {
                    stmt.setString(1, tenant);
                    for (var cp : contents) {
                        if (cp.getContentUrl() != null) {
                            log.trace("Decrementing content counter of {} {}", tenant, cp.getContentUrl());
                            stmt.setString(2, cp.getContentUrl());
                            stmt.executeUpdate();
                        }
                    }

                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public void incrementContentRef(String tenant, ContentProperty cp) {
        incrementContentRef(tenant, List.of(cp));
    }

    public void incrementContentRef(ActiveNode node) {
        incrementContentRef(node.getTenant(), node.getData().getContents());
    }

    public void incrementContentRef(String tenant, List<ContentProperty> contents) {
        if (contents != null && !contents.isEmpty()) {
            var sql = """
                insert into ecm_files (tenant,contentref,contentsize,counter) values (?,?,?,1)\s
                on conflict (tenant,contentref) do update set\s
                counter = ecm_files.counter + excluded.counter\s
                where ecm_files.counter >= 0
                """;
            call(conn -> {
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, tenant);
                    for (var cp : contents) {
                        if (cp.getContentUrl() != null) {
                            log.trace("Incrementing content counter of {} {}", tenant, cp.getContentUrl());
                            stmt.setString(2, cp.getContentUrl());
                            stmt.setLong(3, cp.getSize());
                            stmt.executeUpdate();
                        }
                    }

                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public void unCountContentRef(String dbSchema, String tenant, ContentProperty cp) {
        if (cp != null && cp.getContentUrl() != null) {
            var sql = """
                insert into ecm_files (tenant,contentref,contentsize,counter) values (?,?,?,-1)\s
                on conflict (tenant,contentref) do update set\s
                counter = excluded.counter
                """;
            DBUtils.call(ds, dbSchema, conn -> {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    log.trace("Setting -1 in content counter of {} {}", tenant, cp.getContentUrl());
                    stmt.setString(1, tenant);
                    stmt.setString(2, cp.getContentUrl());
                    stmt.setLong(3, cp.getSize());
                    stmt.executeUpdate();

                    return null;
                } catch (SQLException e) {
                    throw new SystemException(e);
                }
            });
        }
    }

    public Optional<String> findUUIDWherePath(String path) {
        if (StringUtils.isBlank(path)) {
            return Optional.empty();
        }

        var p = StringUtils.endsWith(path, "/") ? path : path + "/";
        var m = mapUUIDInFilePaths(List.of(p));
        return Optional.ofNullable(m.get(p));
    }

    public Map<String,String> mapUUIDInFilePaths(Collection<String> paths) {
        var map = new HashMap<String,String>();
        call(conn -> {
            var sql = """
                select n.uuid,p.file_path\s
                from ecm_nodes n join ecm_paths p on (p.node_id = n.id)\s
                where n.tenant = ? and p.file_path = any(?)
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getContext().getTenantRef().toString());
                stmt.setArray(2, conn.createArrayOf("VARCHAR", paths.toArray(new String[0])));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getString("file_path"), rs.getString("uuid"));
                    }
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }

            return null;
        });

        return map;
    }

    public Collection<ActiveNode> findNodesInUUIDs(Collection<String> uuids, Set<MapOption> optionSet, QueryScope scope) {
        return mapNodesInUUIDs(uuids, optionSet, scope).values();
    }

    public Optional<ActiveNode> findNodeByUUID(String uuid) {
        return findNodeByUUID(uuid, Set.of(DEFAULT), QueryScope.DEFAULT);
    }

    public Optional<ActiveNode> findNodeByUUID(String uuid, Set<MapOption> optionSet, QueryScope scope) {
        return Optional.ofNullable(mapNodesInUUIDs(List.of(uuid), optionSet, scope).get(uuid));
    }

    public Map<String, ActiveNode> mapNodesInUUIDs(Collection<String> uuids, Set<MapOption> optionSet, QueryScope scope) {
        return call(conn -> {
            try {
                var r = mapNodes(conn, optionSet, scope, false, conn.createArrayOf("VARCHAR", uuids.toArray(new String[0])));
                return r.uuidMap;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Map<String, ActiveNode> mapNodesInUUIDs(Collection<String> uuids) {
        return mapNodesInUUIDs(uuids, Set.of(DEFAULT), QueryScope.DEFAULT);
    }

    public Optional<ActiveNode> findNodeById(long id, Set<MapOption> optionSet, QueryScope scope) {
        return Optional.ofNullable(mapNodes(List.of(id), optionSet, scope).get(id));
    }

    public Map<Long, ActiveNode> mapNodes(Collection<Long> ids, Set<MapOption> optionSet, QueryScope scope) {
        return call(conn -> {
            try {
                var r = mapNodes(conn, optionSet, scope, true, conn.createArrayOf("INTEGER", ids.toArray(new Long[0])));
                return r.nodeMap;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ResultMaps mapNodes(Connection conn, Set<MapOption> optionSet, QueryScope scope, boolean useId, Array array) throws SQLException, JsonProcessingException {
        var nodeMap = new HashMap<Long, ActiveNode>();
        var uuidMap = new HashMap<String, ActiveNode>();
        var sgMap = new HashMap<Long, SecurityGroup>();
        var txMap = new HashMap<Long, ApplicationTransaction>();

        try (var stmt = conn.prepareStatement(createSQL(optionSet, useId))) {
            stmt.setString(1, UserContextManager.getContext().getTenantRef().toString());
            stmt.setArray(2, array);

            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var n = readNode(rs, optionSet, sgMap, txMap);
                    nodeMap.put(n.getId(), n);
                    uuidMap.put(n.getUuid(), n);
                } // end while node
            }
        } // end try node

        if (optionSet.contains(PARENT_ASSOCIATIONS) || optionSet.contains(PARENT_HARD_ASSOCIATIONS)) {
            fillParents(conn, nodeMap);
        }

        fillNodeExtra(conn, nodeMap, sgMap, scope == QueryScope.SEARCH && optionSet.contains(PATHS) ? QueryScope.DEFAULT : scope);
        return new ResultMaps(nodeMap, uuidMap, sgMap, txMap);
    }

    private record ResultMaps(Map<Long, ActiveNode> nodeMap, Map<String, ActiveNode> uuidMap, Map<Long, SecurityGroup> sgMap, Map<Long, ApplicationTransaction> txMap) {
    }

    private String createSQL(Set<MapOption> optionSet, boolean useId) {
        var sql = """
                  select n.id,n.version,n.tenant,n.uuid,n.type_name,n.data,n.updated_at,n.sg_id,n.tx,n.tx_flags%s%s\s
                  from ecm_nodes n%s%s\s
                  where n.tenant = ? and n.%s = any (?)
                  """;

        final String txParams, txJoin;
        if (optionSet.contains(MapOption.TX)) {
            txParams = ",t.uuid tx_uuid";
            txJoin = " join ecm_transactions t on (t.id = n.tx)";
        } else {
            txParams = "";
            txJoin = "";
        }

        final String sgParams, sgJoin;
        if (optionSet.contains(MapOption.SG)) {
            sgParams = ",s.uuid sg_uuid,s.managed,s.inheritance";
            sgJoin = " left outer join ecm_security_groups s on (s.id = n.sg_id)";
        } else {
            sgParams = "";
            sgJoin = "";
        }

        return String.format(sql, txParams, sgParams, txJoin, sgJoin, useId ? "id" : "uuid");
    }

    private ActiveNode readNode(ResultSet rs, Set<MapOption> optionSet, Map<Long, SecurityGroup> sgMap, Map<Long, ApplicationTransaction> txMap) throws SQLException, JsonProcessingException {
        var n = new ActiveNode();
        n.setId(rs.getLong("id"));
        n.setVersion(rs.getInt("version"));
        n.setTenant(rs.getString("tenant"));
        n.setUuid(rs.getString("uuid"));
        n.setTypeName(rs.getString("type_name"));

        var data = objectMapper.readValue(rs.getString("data"), NodeData.class);
        UserContextManager.getTenantData()
            .map(TenantData::getImplicitAspects)
            .filter(aspect -> !aspect.isEmpty())
            .ifPresent(aspects -> data.getAspects().addAll(aspects));

        n.getData().copyFrom(data);

        var sgId = rs.getLong("sg_id");
        if (sgId != 0) {
            var sg = sgMap.compute(sgId, (k,v) -> {
                if (v == null) {
                    v = new SecurityGroup();
                    v.setId(k);

                    try {
                        if (optionSet.contains(MapOption.SG)) {
                            v.setUuid(rs.getString("sg_uuid"));
                            v.setManaged(rs.getBoolean("managed"));
                            v.setInheritanceEnabled(rs.getBoolean("inheritance"));
                            v.setTenant(n.getTenant());
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                return v;
            });
            n.setSecurityGroup(sg);
        }

        var txId = rs.getLong("tx");
        if (txId != 0) {
            var tx = txMap.compute(txId, (k,v) -> {
                if (v == null) {
                    v = new ApplicationTransaction();
                    v.setId(k);

                    try {
                        if (optionSet.contains(MapOption.TX)) {
                            v.setUuid(rs.getString("tx_uuid"));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                return v;
            });
            n.setTx(tx);
        }

        return n;
    }

    private void fillParents(Connection conn, Map<Long, ActiveNode> nodeMap) throws SQLException {
        var parentSQL = """
                    select a.id,a.parent_id,n.uuid parent_uuid,a.child_id,a.type_name,a.name,a.code,a.is_hard\s
                    from ecm_associations a\s
                    join ecm_nodes n on (n.id = a.parent_id)\s
                    where a.child_id = any (?)\s
                    order by a.id
                    """;

        try (var stmt = conn.prepareStatement(parentSQL)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", nodeMap.keySet().toArray(new Long[0])));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var p = new Association();
                    p.setId(rs.getLong("id"));

                    var parentId = rs.getLong("parent_id");
                    var parent = nodeMap.get(parentId);
                    if (parent == null) {
                        parent = new ActiveNode();
                        parent.setId(parentId);
                        parent.setUuid(rs.getString("parent_uuid"));
                    }
                    p.setParent(parent);

                    p.setTypeName(rs.getString("type_name"));
                    p.setName(rs.getString("name"));
                    p.setCode(rs.getString("code"));
                    p.setHard(DBUtils.getBoolean(rs, "is_hard"));

                    var child = nodeMap.get(rs.getLong("child_id"));
                    if (child != null) {
                        p.setChild(child);
                        child.getParents().add(p);
                    }
                } // end while parent
            }
        } // end try parent
    }

    private void fillNodeExtra(Connection conn, Map<Long, ActiveNode> nodeMap, Map<Long, SecurityGroup> sgMap, QueryScope scope) throws SQLException {
        if (scope != QueryScope.SEARCH) {
            var pathSQL = """
                    select p.id,p.file_path,p.lev,p.node_path,p.sg_path,p.is_hard,p.node_id\s
                    from ecm_paths p\s
                    where p.node_id = any (?)\s
                    order by p.id
                    """;

            try (var stmt = conn.prepareStatement(pathSQL)) {
                stmt.setArray(1, conn.createArrayOf("INTEGER", nodeMap.keySet().toArray(new Long[0])));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        var p = new NodePath();
                        p.setId(rs.getLong("id"));
                        p.setFilePath(rs.getString("file_path"));
                        p.setLev(rs.getInt("lev"));
                        p.setPath(rs.getString("node_path"));
                        p.setSgPath(rs.getString("sg_path"));
                        p.setHard(rs.getBoolean("is_hard"));

                        var nodeId = rs.getLong("node_id");
                        var n = nodeMap.get(nodeId);
                        if (n != null) {
                            p.setNode(n);
                            n.getPaths().add(p);

                            if (p.getSgPath() != null) {
                                Arrays.stream(p.getSgPath().split(":"))
                                    .filter(StringUtils::isNotBlank)
                                    .map(Long::parseLong)
                                    .forEach(sgId -> sgMap.compute(sgId, (k,v) -> {
                                        if (v == null) {
                                            v = new SecurityGroup();
                                            v.setId(k);
                                        }

                                        return v;
                                    }));
                            }
                        }
                    } // end while path
                }
            } // end try paths

            if (scope == QueryScope.PERMISSIONS) {
                var sgSQL = """
                    select a.id,a.authority,a.rights,a.sg_id\s
                    from ecm_access_rules a\s
                    where a.sg_id = any (?)
                    """;

                try (var stmt = conn.prepareStatement(sgSQL)) {
                    var sgArray = sgMap.values().stream().filter(sg -> sg.getRules().isEmpty()).map(SecurityGroup::getId).toList().toArray(new Long[0]);
                    log.trace("Loading ecm_access_rules for {}", Arrays.stream(sgArray).map(Object::toString).toList());
                    stmt.setArray(1, conn.createArrayOf("INTEGER", sgArray));
                    try (var rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            var a = new AccessRule();
                            a.setId(rs.getLong("id"));
                            a.setAuthority(rs.getString("authority"));
                            a.setRights(rs.getString("rights"));

                            var sgId = rs.getLong("sg_id");
                            var sg = sgMap.get(sgId);
                            if (sg != null) {
                                a.setSecurityGroup(sg);
                                sg.getRules().add(a);
                            }
                        } // end while sg
                    }
                } // end try sg
            }
        }
    }

    public void createSG(SecurityGroup sg) {
        call(conn -> {
            try {
                try (var stmt = conn.prepareStatement("""
                    insert into ecm_security_groups (tenant,name,uuid,tx,inheritance,managed)\s
                    values (?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS))
                {
                    stmt.setString(1, sg.getTenant());
                    stmt.setString(2, sg.getName());
                    stmt.setString(3, sg.getUuid());
                    stmt.setLong(4, sg.getTx().getId());
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

                    log.debug("Security Group {} created", sg.getId());
                }

                try (var stmt = conn.prepareStatement("""
                    insert into ecm_access_rules (sg_id,authority,rights)\s
                    values (?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS))
                {
                    stmt.setLong(1, sg.getId());
                    int count = 0;
                    for (var ar : sg.getRules()) {
                        stmt.setString(2, ar.getAuthority());
                        stmt.setString(3, ar.getRights());

                        if (stmt.executeUpdate() < 1) {
                            throw new RuntimeException("Unable to create access rule");
                        }

                        try (var generatedKeys = stmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                ar.setId(generatedKeys.getLong(1));
                            } else {
                                throw new RuntimeException("Access Rule creation failed, no ID obtained.");
                            }
                        }

                        count++;
                    } // end for ar
                    log.debug("{} access rules created for SG {}", count, sg.getId());
                }

                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void copySubNodes(ApplicationTransaction tx, ActiveNode source, ActiveNode target, AtomicLong counter) {
        call(conn -> {
            try {
                try (var stmt = conn.prepareStatement("""
                    insert into ecm_nodes (data, tenant, type_name, updated_at, uuid, sg_id, tx, tx_flags)\s
                    select\s
                        jsonb_set(\s
                            jsonb_set(\s
                                case when jsonb_exists(n.data->'aspects', 'cm:copiedfrom')\s
                                then n.data\s
                                else jsonb_set(n.data, '{aspects}', n.data->'aspects' || '"cm:copiedfrom"')\s
                                end,
                                '{properties}',
                                n.data->'properties' || cast('{"cm:source": "' || n.uuid || '"}' as jsonb)\s
                            ), '{"internals"}',
                            cast('{"ecm-sys:source-DBID": ' || n.id || '}' as jsonb)\s
                        ), n.tenant,
                        n.type_name, now(), gen_random_uuid(), n.sg_id, ?, ?\s
                    from ecm_nodes n where n.id in (\s
                      select node_id from ecm_paths p\s
                      where p.is_hard and p.path_parts @> ? and p.node_id != ?
                    )
                    order by n.id
                    """))
                {
                    stmt.setLong(1, tx.getId());
                    stmt.setString(2, IndexingFlags.formatAsBinary(IndexingFlags.FULL_FLAG_MASK));

                    var parts = new Long[1];
                    parts[0] = source.getId();
                    stmt.setArray(3, conn.createArrayOf("INTEGER", parts));
                    stmt.setLong(4, parts[0]);

                    int n = stmt.executeUpdate();
                    log.debug("{} children nodes copied", n);
                    if (counter != null) {
                        counter.addAndGet(n);
                    }
                } catch (SQLException e) {
                    log.error("Error copying children: {}", e.getMessage());
                    throw e;
                }

                try (var stmt = conn.prepareStatement("""
                    insert into ecm_associations (is_hard, name, type_name, child_id, parent_id, code)\s
                    select a.is_hard, a.name, a.type_name, case when child.id is null then a.child_id else child.id end, parent.id, a.code\s
                    from ecm_associations a\s
                    join ecm_nodes parent on a.parent_id = (parent.data->'internals'->>'ecm-sys:source-DBID')::integer\s
                    left outer join ecm_nodes child on a.child_id = (child.data->'internals'->>'ecm-sys:source-DBID')::integer\s
                    where parent.tx = ? and child.tx = ?
                    """))
                {
                    stmt.setLong(1, tx.getId());
                    stmt.setLong(2, tx.getId());
                    int n = stmt.executeUpdate();
                    log.debug("{} new associations generated", n);
                } catch (SQLException e) {
                    log.error("Error generating new associations: {}", e.getMessage());
                    throw e;
                }

                try (var stmt = conn.prepareStatement("""
                    insert into ecm_security_groups (name, tenant, tx, inheritance, managed, sg_src)\s
                    select s.name, s.tenant, ?, s.inheritance, s.managed, n.sg_id\s
                    from ecm_security_groups s join ecm_nodes n on s.id = n.sg_id and s.managed\s
                    where n.tx = ? and n.data->'internals'->'ecm-sys:source-DBID' is not null\s
                    and n.id != ?
                    """))
                {
                    stmt.setLong(1, tx.getId());
                    stmt.setLong(2, tx.getId());
                    stmt.setLong(3, target.getId());
                    int n = stmt.executeUpdate();
                    log.debug("{} SG{} generated", n, (n == 1 ? "" : "s"));
                } catch (SQLException e) {
                    log.error("Error generating new SGs: {}", e.getMessage());
                    throw e;
                }

                try (var stmt = conn.prepareStatement("""
                    update ecm_nodes set sg_id = s.id\s
                    from (select id, sg_src from ecm_security_groups where tx = ? and managed = true) s\s
                    where sg_id = s.sg_src and tx = ?
                    """))
                {
                    stmt.setLong(1, tx.getId());
                    stmt.setLong(2, tx.getId());
                    int n = stmt.executeUpdate();
                    log.debug("{} updated managed SG{}", n, (n == 1 ? "" : "s"));
                } catch (SQLException e) {
                    log.error("Error updating sg_id of managed nodes");
                    throw e;
                }

                try (var stmt = conn.prepareStatement("""
                    insert into ecm_access_rules(authority, rights, sg_id)\s
                    select a.authority, a.rights, s.id\s
                    from ecm_access_rules a join ecm_security_groups s on s.sg_src = a.sg_id\s
                    where s.tx = ?
                    """)) {
                    stmt.setLong(1, tx.getId());
                    int n = stmt.executeUpdate();
                    log.debug("{} access rules inserted", n);
                } catch (SQLException e) {
                    log.error("Error inserting access rules into new SGs");
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return null;
        });
    }

    public void setNameOfDescendingNodes(ApplicationTransaction tx, ActiveNode copiedNode, CopyMode copyMode) {
        call(conn -> {
            try {
                var queries = createRenamingQueries(copyMode);
                try (var stmt = conn.prepareStatement(queries.get("nodes"))) {
                    stmt.setLong(1, tx.getId());
                    stmt.setLong(2, copiedNode.getId());
                    int n = stmt.executeUpdate();
                    log.debug("{} nodes updated with the indicated name", n);
                } catch (SQLException e) {
                    log.error("Error in updating cm:name");
                    throw e;
                }

                try (var stmt = conn.prepareStatement(queries.get("associations"))) {
                    stmt.setLong(1, tx.getId());
                    stmt.setLong(2, copiedNode.getId());
                    int n = stmt.executeUpdate();
                    log.debug("{} associations updated with the indicated name", n);
                } catch (SQLException e) {
                    log.error("Error in updating association name");
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public int setTxDescendingOfNode(ApplicationTransaction tx, ActiveNode node, boolean excludeCopied) {
        return call(conn -> {
            var sql = """
                update ecm_nodes n set tx = ?\s
                where n.id in (
                  select p.node_id\s
                  from ecm_paths p\s
                  where p.path_parts @> ? and p.node_id != ?
                )
                """;

            if (excludeCopied) {
                sql += """
                     and n.id::text not in (
                    select data->'internals'->>'ecm-sys:source-DBID'\s
                    from ecm_nodes where tx = ?
                    )
                    """;
            }

            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, tx.getId());

                var parts = new Long[1];
                parts[0] = node.getId();

                stmt.setArray(2, conn.createArrayOf("INTEGER", parts));
                stmt.setLong(3, parts[0]);

                if (excludeCopied) {
                    stmt.setLong(4, tx.getId());
                }

                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private HashMap<String, String> createRenamingQueries(CopyMode copyMode) {
        var m = new HashMap<String, String>();
        switch (copyMode) {
            case UUID -> {
                m.put("nodes", """
                    update ecm_nodes\s
                    set data = jsonb_set(data, '{properties, cm:name}', to_jsonb(uuid))\s
                    where tx = ? and id != ?
                    """);
                m.put("associations", """
                    update ecm_associations\s
                    set name = 'cm:' || n.uuid, code = lower('cm:' || n.uuid)\s
                    from ecm_nodes n\s
                    where n.id = ecm_associations.child_id and n.tx = ? and n.id != ?
                    """);
            }
            case DBID -> {
                m.put("nodes", """
                    update ecm_nodes\s
                    set data = jsonb_set(data, '{properties, cm:name}', to_jsonb(id))\s
                    where tx = ? and id != ?
                    """);
                m.put("associations", """
                    update ecm_associations\s
                    set name = 'cm:' || n.id, code = lower('cm:' || n.id)\s
                    from ecm_nodes n\s
                    where n.id = ecm_associations.child_id and n.tx = ? and n.id != ?
                    """);
            }
            default -> {
                m.put("nodes", """
                    update ecm_nodes\s
                    set data = jsonb_set(data, '{properties, cm:name}', to_jsonb(data->'properties'->>'cm:name'))\s
                    where tx = ? and id != ?
                    """);
                m.put("associations", """
                    update ecm_associations\s
                    set name = data->'properties'->>'cm:name', code = lower(data->'properties'->>'cm:name')\s
                    from ecm_nodes n\s
                    where n.id = ecm_associations.child_id and n.tx = ? and n.id != ?
                    """);
            }
        }
        return m;
    }
}

package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.domain.policy.QueryScope;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.policy.MapOption;
import it.doqui.libra.librabl.foundation.exceptions.LockedException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.*;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import it.doqui.libra.librabl.domain.model.graph.VertexType;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

import static it.doqui.libra.librabl.domain.policy.MapOption.*;

@Slf4j
public class NodeLoader {
    private final DataSource ds;
    private final ObjectMapper objectMapper;
    private final String schema;
    private final String tenant;
    private final Set<MapOption> optionSet;
    private final QueryScope scope;
    private final TenantData tenantData;

    public NodeLoader(DataSource ds, ObjectMapper objectMapper, QueryContext queryContext, TenantData tenantData) {
        this.ds = ds;
        this.objectMapper = objectMapper;
        this.schema = queryContext.getSchema();
        this.tenant = queryContext.getTenant();
        this.optionSet = queryContext.getOptionSet();
        this.scope = queryContext.getScope();
        this.tenantData = tenantData;
    }

    private  <R> R call(Function<Connection,R> f) {
        return DBUtils.call(ds, schema, f);
    }

    ResultMaps lookupNodes(Collection<Vertex> vertexes) {
        return call(conn -> {
            try {
                var maps = new ResultMaps();
                retrieveNodes(conn, vertexes, maps, VertexType.ID);
                retrieveNodes(conn, vertexes, maps, VertexType.UUID);
                retrieveNodes(conn, vertexes, maps, VertexType.PATH);
                return maps;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    void retrieveNodes(Connection conn, Collection<Vertex> vertexes, ResultMaps resultMaps,VertexType vertexType) throws SQLException, JsonProcessingException {
        Array array = switch (vertexType) {
            case ID -> {
                var ids = vertexes.stream()
                        .filter(v -> v.getType() == VertexType.ID)
                        .map(Vertex::getValue)
                        .map(Long::parseLong)
                        .filter(id -> !resultMaps.idMap().containsKey(id))
                        .toList();

                yield ids.isEmpty() ? null : conn.createArrayOf("INTEGER", ids.toArray(new Long[0]));
            }

            case UUID -> {
                var uuids = vertexes.stream()
                        .filter(v -> v.getType() == VertexType.UUID)
                        .map(Vertex::getValue)
                        .filter(uuid -> !resultMaps.uuidMap().containsKey(uuid))
                        .toList();

                yield uuids.isEmpty() ? null : conn.createArrayOf("VARCHAR", uuids.toArray(new String[0]));
            }

            case PATH -> {
                var paths = vertexes.stream()
                        .filter(v -> v.getType() == VertexType.PATH)
                        .map(Vertex::getValue)
                        .filter(path -> !resultMaps.pathMap().containsKey(path))
                        .toList();

                yield paths.isEmpty() ? null : conn.createArrayOf("VARCHAR", paths.toArray(new String[0]));
            }
        };

        if (array == null) {
            return;
        }

        var maps = new ResultMaps();
        try (var stmt = conn.prepareStatement(createSQL(vertexType))) {
            stmt.setString(1, tenant);
            stmt.setArray(2, array);

            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var n = readNode(rs, maps.sgMap(), maps.txMap());
                    if (scope == QueryScope.UPDATE && n.getData().isLocked(NodeData.LockType.WRITE)) {
                        throw new LockedException("Node " + n.getUuid() + " is locked");
                    }
                    maps.idMap().put(n.getId(), n);
                    maps.uuidMap().put(n.getUuid(), n);
                } // end while node
            }
        } // end try node

        if (optionSet.contains(PARENT_ASSOCIATIONS) || optionSet.contains(PARENT_HARD_ASSOCIATIONS)) {
            fillParents(conn, maps.idMap());
        }

        if (optionSet.contains(EXTERNAL_PROPERTIES)) {
            fillExternalProperties(conn, maps.idMap());
        }

        fillNodeExtra(conn, maps,
                vertexType == VertexType.PATH || (scope == QueryScope.SEARCH && optionSet.contains(PATHS))
                        ? QueryScope.DEFAULT
                        : scope);

        resultMaps.idMap().putAll(maps.idMap());
        resultMaps.uuidMap().putAll(maps.uuidMap());
        resultMaps.pathMap().putAll(maps.pathMap());
        resultMaps.sgMap().putAll(maps.sgMap());
        resultMaps.txMap().putAll(maps.txMap());
    }

    private String createSQL(VertexType vertexType) {
        var sql = "select n.id,n.version,n.tenant,n.uuid,n.type_name,n.code,n.data,n.updated_at,n.sg_id,n.tx,n.tx_flags";

        if (optionSet.contains(MapOption.SG)) {
            sql += ",s.uuid sg_uuid,s.managed,s.inheritance";
        }

        sql += " from ecm_nodes n";

        if (vertexType == VertexType.PATH) {
            sql += " join ecm_paths p on (p.node_id = n.id)";
        }

        if (optionSet.contains(MapOption.SG)) {
            sql += " left outer join ecm_security_groups s on (s.id = n.sg_id)";
        }

        sql += " where n.tenant = ? and ";

        sql += switch (vertexType) {
            case ID -> "n.id = any (?)";
            case UUID -> "n.uuid = any (?)";
            case PATH -> "p.file_path = any (?)";
        };

        if (scope == QueryScope.UPDATE || scope == QueryScope.UNLOCK) {
            sql += " for update";
        }

        return sql;
    }

    private ActiveNode readNode(ResultSet rs, Map<Long, SecurityGroup> sgMap, Map<Long, ApplicationTransaction> txMap) throws SQLException, JsonProcessingException {
        var n = new ActiveNode();
        n.setId(rs.getLong("id"));
        n.setVersion(rs.getInt("version"));
        n.setTenant(rs.getString("tenant"));
        n.setUuid(rs.getString("uuid"));
        n.setTypeName(rs.getString("type_name"));
        n.setCode(rs.getString("code"));

        var data = objectMapper.readValue(rs.getString("data"), NodeData.class);
        Optional.ofNullable(tenantData)
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
                }

                return v;
            });
            n.setTx(tx);
        }

        return n;
    }

    private void fillParents(Connection conn, Map<Long, ActiveNode> idMap) throws SQLException {
        var parentSQL = """
                    select a.id,a.parent_id,n.uuid parent_uuid,a.child_id,a.type_name,a.name,a.code,a.is_hard\s
                    from ecm_associations a\s
                    join ecm_nodes n on (n.id = a.parent_id)\s
                    where a.child_id = any (?)\s
                    order by a.id
                    """;

        try (var stmt = conn.prepareStatement(parentSQL)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", idMap.keySet().toArray(new Long[0])));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var p = new Association();
                    p.setId(rs.getLong("id"));

                    var parentId = rs.getLong("parent_id");
                    var parent = idMap.get(parentId);
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

                    var child = idMap.get(rs.getLong("child_id"));
                    if (child != null) {
                        p.setChild(child);
                        child.getParents().add(p);
                    }
                } // end while parent
            }
        } // end try parent
    }

    private void fillExternalProperties(Connection conn, Map<Long, ActiveNode> idMap) throws SQLException {
        var sql = """
                select ep.node_id,ep.prop_name,ep.prop_value\s
                from ecm_external_properties ep\s
                where ep.node_id = any (?)\s
                order by ep.node_id
                """;

        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", idMap.keySet().toArray(new Long[0])));
            try (var rs = stmt.executeQuery()) {
                ActiveNode node = null;
                while (rs.next()) {
                    var nodeId = rs.getLong("node_id");
                    if (node == null || !node.getId().equals(nodeId)) {
                        node = idMap.get(nodeId);
                    }

                    if (node != null) {
                        node.getExternalProperties().put(rs.getString("prop_name"), rs.getString("prop_value"));
                    }
                }
            }
        }
    }

    private void fillNodeExtra(Connection conn, ResultMaps maps, QueryScope scope) throws SQLException {
        if (scope != QueryScope.SEARCH) {
            var pathSQL = """
                    select p.id,p.file_path,p.lev,p.node_path,p.sg_path,p.is_hard,p.node_id\s
                    from ecm_paths p\s
                    where p.node_id = any (?)\s
                    order by p.id
                    """;

            try (var stmt = conn.prepareStatement(pathSQL)) {
                stmt.setArray(1, conn.createArrayOf("INTEGER", maps.idMap().keySet().toArray(new Long[0])));
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
                        var n = maps.idMap().get(nodeId);
                        if (n != null) {
                            p.setNode(n);
                            n.getPaths().add(p);

                            if (p.getSgPath() != null) {
                                Arrays.stream(p.getSgPath().split(":"))
                                        .filter(StringUtils::isNotBlank)
                                        .map(Long::parseLong)
                                        .forEach(sgId -> maps.sgMap().compute(sgId, (k, v) -> {
                                            if (v == null) {
                                                v = new SecurityGroup();
                                                v.setId(k);
                                            }

                                            return v;
                                        }));
                            }

                            maps.pathMap().put(p.getFilePath(), n);
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
                    var sgArray = maps.sgMap().values().stream().filter(sg -> sg.getRules().isEmpty()).map(SecurityGroup::getId).toList().toArray(new Long[0]);
                    log.trace("Loading ecm_access_rules for {}", Arrays.stream(sgArray).map(Object::toString).toList());
                    stmt.setArray(1, conn.createArrayOf("INTEGER", sgArray));
                    try (var rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            var a = new AccessRule();
                            a.setId(rs.getLong("id"));
                            a.setAuthority(rs.getString("authority"));
                            a.setRights(rs.getString("rights"));

                            var sgId = rs.getLong("sg_id");
                            var sg = maps.sgMap().get(sgId);
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
}

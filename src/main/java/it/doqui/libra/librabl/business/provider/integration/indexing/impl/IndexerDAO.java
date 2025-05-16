package it.doqui.libra.librabl.business.provider.integration.indexing.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.data.dao.AbstractDAO;
import it.doqui.libra.librabl.business.provider.data.entities.*;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.interfaces.Constants;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.*;

@ApplicationScoped
@Slf4j
public class IndexerDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    public Optional<ApplicationTransaction> findTransactionByIdOptional(Connection conn, long txId) throws SQLException {
        final String sql = "select id,tenant,uuid,created_at,indexed_at " +
            "from ecm_transactions where id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, txId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(readTx(rs));
            }
        }
    }

    public Map<Long,ApplicationTransaction> mapTransactions(Connection conn, String tenant, Collection<Long> txIds) throws SQLException {
        final String sql = "select id,tenant,uuid,created_at,indexed_at " +
            "from ecm_transactions where id = any (?) and tenant = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", txIds.toArray(new Long[0])));
            stmt.setString(2, tenant);
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Long,ApplicationTransaction> map = new LinkedHashMap<>();
                while (rs.next()) {
                    var tx = readTx(rs);
                    map.put(tx.getId(), tx);
                }

                return map;
            }
        }
    }

    public void removeDeletedNodes(Connection conn, List<Long> deletedIDs) throws SQLException {
        final String sql = "delete from ecm_nodes where id = any (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            log.debug("Removing deleted nodes: {}", deletedIDs);
            stmt.setArray(1, conn.createArrayOf("INTEGER", deletedIDs.toArray(new Long[0])));
            int n = stmt.executeUpdate();
            log.info("{} ecm-sys:deleted nodes removed", n);
        }
    }

    public void setTransactionIndexedNow(Connection conn, Collection<Long> txIds) throws SQLException {
        final String sql = "update ecm_transactions set indexed_at = ? where id = any (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            stmt.setArray(2, conn.createArrayOf("INTEGER", txIds.toArray(new Long[0])));
            stmt.executeUpdate();
        }
    }

    public List<String> findArchivedUUIDsWithTx(Connection conn, Collection<Long> txIds, Pageable pageable) throws SQLException {
        return findUUIDsWithTxFromTable(conn,txIds,"ecm_archived_nodes", pageable);
    }

    public List<String> findRemovedUUIDsWithTx(Connection conn, Collection<Long> txIds, Pageable pageable) throws SQLException {
        return findUUIDsWithTxFromTable(conn,txIds,"ecm_removed_nodes", pageable);
    }

    private List<String> findUUIDsWithTxFromTable(Connection conn, Collection<Long> txIds, String table, Pageable pageable) throws SQLException {
        String sql = String.format("select uuid from %s where tx = any (?)", table);
        if (pageable != null) {
            sql += " order by id limit ? offset ?";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int c = 0;
            stmt.setArray(++c, conn.createArrayOf("INTEGER", txIds.toArray(new Long[0])));
            if (pageable != null) {
                stmt.setInt(++c, pageable.getSize());
                stmt.setInt(++c, pageable.getPage() * pageable.getSize());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<String> uuids = new LinkedList<>();
                while (rs.next()) {
                    uuids.add(rs.getString("uuid"));
                }
                return uuids;
            }
        }
    }

    public List<String> findArchivedUUIDs(Connection conn, Collection<Long> ids) throws SQLException {
        return findUUIDsFromTable(conn,ids,"ecm_archived_nodes");
    }

    public List<String> findRemovedUUIDs(Connection conn, Collection<Long> ids) throws SQLException {
        return findUUIDsFromTable(conn,ids,"ecm_removed_nodes");
    }

    private List<String> findUUIDsFromTable(Connection conn, Collection<Long> ids, String table) throws SQLException {
        String sql = String.format("select uuid from %s where id = any (?)", table);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", ids.toArray(new Long[0])));
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> uuids = new LinkedList<>();
                while (rs.next()) {
                    uuids.add(rs.getString("uuid"));
                }
                return uuids;
            }
        }
    }

    public Collection<SecurityGroup> findSGWithTx(Connection conn, Map<Long,ApplicationTransaction> txMap, String tenant, Pageable pageable) throws SQLException {
        final Map<Long,SecurityGroup> sgMap = new HashMap<>();

        var sql = """
            select s.id sg_id,s.tx,a.id,a.authority,a.rights\s
            from ecm_security_groups s\s
            left outer join ecm_access_rules a on (s.id = a.sg_id)\s
            where s.tx = any (?) and s.tenant = ?
            """;

        if (pageable != null) {
            sql += " order by s.id,a.id limit ? offset ?";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int c = 0;
            stmt.setArray(++c, conn.createArrayOf("INTEGER", txMap.keySet().toArray(new Long[0])));
            stmt.setString(++c, tenant);
            if (pageable != null) {
                stmt.setInt(++c, pageable.getSize());
                stmt.setInt(++c, pageable.getPage() * pageable.getSize());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long sgId = rs.getLong("sg_id");
                    SecurityGroup sg = sgMap.get(sgId);
                    if (sg == null) {
                        sg = new SecurityGroup();
                        sg.setId(sgId);
                        sg.setTenant(tenant);
                        sg.setTx(txMap.get(rs.getLong("tx")));
                        sgMap.put(sgId, sg);
                    }

                    long arId = rs.getLong("id");
                    if (arId != 0) {
                        var rights = rs.getString("rights");
                        if (StringUtils.startsWith(rights, "1")) {
                            AccessRule ar = new AccessRule();
                            ar.setId(arId);
                            ar.setAuthority(rs.getString("authority"));
                            ar.setRights(rights);
                            sg.getRules().add(ar);
                        }
                    }
                }
            }
        }

        return sgMap.values();
    }

    public Collection<SecurityGroup> findSGOfNodes(Connection conn, String tenant, Collection<Long> nodeIds, Map<Long,ApplicationTransaction> txMap) throws SQLException {
        final Map<Long,SecurityGroup> sgMap = new HashMap<>();

        String sql = "select a.sg_id,s.tx,a.id,a.authority,a.rights,t.uuid tx_uuid,t.created_at tx_created_at " +
            "from ecm_access_rules a " +
            "join ecm_security_groups s on (s.id = a.sg_id) " +
            "join ecm_nodes n on (n.sg_id = s.id) " +
            "join ecm_transactions t on (t.id = s.tx) " +
            "where n.id = any (?) and s.tenant = ? and a.rights like '1%'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", nodeIds.toArray(new Long[0])));
            stmt.setString(2, tenant);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long sgId = rs.getLong("sg_id");
                    SecurityGroup sg = sgMap.get(sgId);
                    if (sg == null) {
                        sg = new SecurityGroup();
                        sg.setId(sgId);
                        sg.setTenant(tenant);

                        long txId = rs.getLong("tx");
                        ApplicationTransaction tx;
                        if (txMap == null) {
                            tx = new ApplicationTransaction();
                            tx.setId(txId);
                        } else {
                            tx = txMap.get(txId);
                            if (tx == null) {
                                tx = new ApplicationTransaction();
                                tx.setId(txId);
                                txMap.put(txId, tx);
                            }
                        }

                        if (tx.getUuid() == null) {
                            tx.setUuid(rs.getString("tx_uuid"));
                            tx.setCreatedAt(DBUtils.getZonedDateTime(rs, "tx_created_at"));
                        }

                        sg.setTx(tx);
                        sgMap.put(sgId, sg);
                    }

                    AccessRule ar = new AccessRule();
                    ar.setId(rs.getLong("id"));
                    ar.setAuthority(rs.getString("authority"));
                    ar.setRights(rs.getString("rights"));
                    sg.getRules().add(ar);
                }
            }
        }

        return sgMap.values();
    }

    public Collection<ActiveNode> findNodes(Connection conn, Collection<Long> ids, Map<Long,ApplicationTransaction> txMap, boolean indexingDisabled) throws SQLException {
        try {
            final Map<Long,ActiveNode> nodeMap = new HashMap<>();

            final var sql = """
                select n.id,n.tenant,n.uuid,n.type_name,n.data,n.updated_at,n.sg_id,n.tx,n.tx_flags,
                f.contentref,f.cached_text,
                x.uuid tx_uuid,x.created_at tx_created_at\s
                from ecm_nodes n\s
                join ecm_transactions x on (x.id = n.tx)\s
                left outer join jsonb_array_elements(n.data->'contents') c on true\s
                left outer join ecm_files f on (f.tenant = n.tenant and f.contentref = c->>'contentUrl')\s
                where n.id = any (?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int c = 0;
                stmt.setArray(++c, conn.createArrayOf("INTEGER", ids.toArray(new Long[0])));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        var n = readActiveNode(rs, nodeMap, txMap, indexingDisabled);
                        if (n != null) {
                            var tx = n.getTx();
                            if (tx.getUuid() == null) {
                                tx.setUuid(rs.getString("tx_uuid"));
                                tx.setCreatedAt(DBUtils.getZonedDateTime(rs, "tx_created_at"));
                            }
                        }
                    }
                }
            }

            fillParents(conn, nodeMap);
            fillPaths(conn, nodeMap);

            return nodeMap.values();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<ActiveNode> findNodesWithTx(Connection conn, Map<Long,ApplicationTransaction> txMap, Set<String> includedUUIDs, Set<String> excludedUUIDs, Pageable pageable, boolean indexingDisabled) throws SQLException {
        try {
            final Map<Long,ActiveNode> nodeMap = new HashMap<>();
            var sql = """
                select n.id,n.tenant,n.uuid,n.type_name,n.data,n.updated_at,n.sg_id,n.tx,n.tx_flags,
                f.contentref,f.cached_text\s
                from (
                select * from ecm_nodes n\s
                where n.tx = any (?)
                """;

            if (includedUUIDs != null) {
                sql += " and n.uuid = any (?)";
            }

            if (excludedUUIDs != null) {
                sql += " and not (n.uuid = any (?))";
            }

            if (pageable != null) {
                sql += " order by id limit ? offset ?";
            }

            sql += """
                ) n\s
                left outer join jsonb_array_elements(n.data->'contents') c on true\s
                left outer join ecm_files f on (f.tenant = n.tenant and f.contentref = c->>'contentUrl')
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int c = 0;
                stmt.setArray(++c, conn.createArrayOf("INTEGER", txMap.keySet().toArray(new Long[0])));

                if (includedUUIDs != null) {
                    stmt.setArray(++c, conn.createArrayOf("VARCHAR", includedUUIDs.toArray(new String[0])));
                }

                if (excludedUUIDs != null) {
                    stmt.setArray(++c, conn.createArrayOf("VARCHAR", excludedUUIDs.toArray(new String[0])));
                }

                if (pageable != null) {
                    stmt.setInt(++c, pageable.getSize());
                    stmt.setInt(++c, pageable.getPage() * pageable.getSize());
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        readActiveNode(rs, nodeMap, txMap, indexingDisabled);
                    }
                }
            }

            fillParents(conn, nodeMap);
            fillPaths(conn, nodeMap);

            return nodeMap.values();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<Long,Long> mapSecurityGroups(Connection conn, Collection<ActiveNode> nodes) throws SQLException {
        var map = new HashMap<Long,Long>();

        var nodeIDs = nodes.stream()
            .map(ActiveNode::getPaths)
            .flatMap(Collection::stream)
            .map(NodePath::getSgPath)
            .filter(Objects::nonNull)
            .flatMap(s -> Arrays.stream(s.split(":")))
            .filter(StringUtils::isNotBlank)
            .map(Long::parseLong)
            .toList();

        var sql = "select n.id, n.sg_id from ecm_nodes n where n.id = any (?)";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", nodeIDs.toArray(new Long[0])));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getLong("id"), rs.getLong("sg_id"));
                }
            }
        }

        return map;
    }

    private ActiveNode readActiveNode(ResultSet rs, Map<Long,ActiveNode> nodeMap, Map<Long,ApplicationTransaction> txMap, boolean indexingDisabled) throws SQLException, JsonProcessingException {
        var id = rs.getLong("id");
        var n = nodeMap.get(id);
        if (n == null) {
            n = new ActiveNode();
            n.setId(id);

            n.setTenant(rs.getString("tenant"));
            n.setUuid(rs.getString("uuid"));
            n.setTypeName(rs.getString("type_name"));

            NodeData data = objectMapper.readValue(rs.getString("data"), NodeData.class);
            UserContextManager.getTenantData()
                .map(TenantData::getImplicitAspects)
                .filter(aspect -> !aspect.isEmpty())
                .ifPresent(aspects -> data.getAspects().addAll(aspects));

            if (indexingDisabled && !data.getAspects().contains(Constants.ASPECT_ECMSYS_INDEXING_REQUIRED)) {
                return null;
            }

            n.getData().copyFrom(data);
            n.setUpdatedAt(DBUtils.getZonedDateTime(rs, "updated_at"));

            Long sgId = DBUtils.getLong(rs, "sg_id");
            if (sgId != null) {
                SecurityGroup sg = new SecurityGroup();
                sg.setId(sgId);
                n.setSecurityGroup(sg);
            }

            long txId = rs.getLong("tx");
            ApplicationTransaction tx;
            if (txMap == null) {
                tx = new ApplicationTransaction();
                tx.setId(txId);
            } else {
                tx = txMap.get(txId);
                if (tx == null) {
                    tx = new ApplicationTransaction();
                    tx.setId(txId);
                    txMap.put(txId, tx);
                }
            }

            n.setTx(tx);
            n.setTransactionFlags(rs.getString("tx_flags"));

            nodeMap.put(n.getId(), n);
        }

        var contentUrl = rs.getString("contentref");
        var text = rs.getString("cached_text");
        if (contentUrl != null && text != null) {
            n.getData().getContents()
                .stream()
                .filter(cp -> StringUtils.equals(cp.getContentUrl(), contentUrl))
                .forEach(cp -> cp.setText(text));
        }

        return n;
    }

    private void fillParents(Connection conn, final Map<Long,ActiveNode> nodeMap) throws SQLException {
        final String sql =
            "select a.child_id,a.id,a.parent_id,n.tenant,n.uuid,a.type_name,a.name,a.is_hard " +
                "from ecm_associations a " +
                "join ecm_nodes n on (n.id = a.parent_id) " +
                "where a.child_id = any (?) " +
                "order by a.child_id,a.is_hard desc,a.id";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", nodeMap.keySet().toArray(new Long[0])));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long childId = rs.getLong("child_id");
                    ActiveNode child = nodeMap.get(childId);
                    if (child != null) {
                        Association a = new Association();
                        a.setId(rs.getLong("id"));
                        a.setTypeName(rs.getString("type_name"));
                        a.setName(rs.getString("name"));
                        a.setHard(DBUtils.getBoolean(rs,"is_hard"));
                        a.setChild(child);

                        ActiveNode parent = new ActiveNode();
                        parent.setId(rs.getLong("parent_id"));
                        parent.setTenant(rs.getString("tenant"));
                        parent.setUuid(rs.getString("uuid"));
                        a.setParent(parent);

                        child.getParents().add(a);
                    }
                }
            }
        }
    }

    private void fillPaths(Connection conn, final Map<Long,ActiveNode> nodeMap) throws SQLException {
        final String sql = "select p.node_id,p.node_path,p.sg_path,p.file_path,p.lev,p.is_hard " +
                "from ecm_paths p where p.node_id = any (?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", nodeMap.keySet().toArray(new Long[0])));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long nodeId = rs.getLong("node_id");
                    ActiveNode node = nodeMap.get(nodeId);
                    if (node != null) {
                        NodePath p = new NodePath();
                        p.setNode(node);
                        p.setPath(rs.getString("node_path"));
                        p.setSgPath(rs.getString("sg_path"));
                        p.setFilePath(rs.getString("file_path"));
                        p.setLev(rs.getInt("lev"));
                        p.setHard(rs.getBoolean("is_hard"));
                        node.getPaths().add(p);
                    }
                }
            }
        }
    }
}

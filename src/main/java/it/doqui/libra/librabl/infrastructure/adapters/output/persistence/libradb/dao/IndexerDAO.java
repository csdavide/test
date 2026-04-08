package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.domain.model.graph.Constants;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.*;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.*;

@ApplicationScoped
@Slf4j
public class IndexerDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TenantRepository tenantRepository;

    @ConfigProperty(name = "libra.content-store.tenant-isolation", defaultValue = "true")
    boolean globalTenantIsolationEnabled;

    public void removeDeletedNodes(Connection conn, List<Long> deletedIDs) throws SQLException {
        final String sql = "delete from ecm_nodes where id = any (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            log.debug("Removing deleted nodes: {}", deletedIDs);
            stmt.setArray(1, conn.createArrayOf("INTEGER", deletedIDs.toArray(new Long[0])));
            int n = stmt.executeUpdate();
            log.info("{} ecm-sys:deleted nodes removed", n);
        }
    }

    public void setTransactionIndexedNow(Connection conn, Collection<Long> ids, Boolean checkRequired) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        final String sql = "update ecm_transactions set indexed_at = ?, ck_required = ? where id = any (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            stmt.setObject(2, checkRequired);
            stmt.setArray(3, conn.createArrayOf("INTEGER", ids.toArray(new Long[0])));
            stmt.executeUpdate();
        }
    }

    public void setNodesIndexedNow(Connection conn, Collection<Long> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        final String sql = "update ecm_nodes set indexed_at = ? where id = any (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            stmt.setArray(2, conn.createArrayOf("INTEGER", ids.toArray(new Long[0])));
            stmt.executeUpdate();
        }
    }

    public void setSGsIndexedNow(Connection conn, Collection<Long> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        final String sql = "update ecm_security_groups set indexed_at = ? where id = any (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            stmt.setArray(2, conn.createArrayOf("INTEGER", ids.toArray(new Long[0])));
            stmt.executeUpdate();
        }
    }

    public List<DeletedNodeRef> findArchivedUUIDsWithTx(Connection conn, Collection<Long> txIds, Pageable pageable) throws SQLException {
        return findUUIDsWithTxFromTable(conn,txIds,"ecm_archived_nodes", pageable);
    }

    public List<DeletedNodeRef> findRemovedUUIDsWithTx(Connection conn, Collection<Long> txIds, Pageable pageable) throws SQLException {
        return findUUIDsWithTxFromTable(conn,txIds,"ecm_removed_nodes", pageable);
    }

    public List<DeletedNodeRef> findTransactionNodesUUIDsWithTx(Connection conn, Collection<Long> txIds, Pageable pageable) throws SQLException {
        return findUUIDsWithTxFromTable(conn, txIds, "ecm_transaction_nodes", pageable);
    }

    private List<DeletedNodeRef> findUUIDsWithTxFromTable(Connection conn, Collection<Long> txIds, String table, Pageable pageable) throws SQLException {
        String sql = String.format("select uuid,tx from %s where tx = any (?)", table);
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
                List<DeletedNodeRef> nodes = new LinkedList<>();
                while (rs.next()) {
                    nodes.add(new DeletedNodeRef(rs.getString("uuid"), rs.getLong("tx")));
                }
                return nodes;
            }
        }
    }

    public record DeletedNodeRef (String uuid, long txId) {}

    public Collection<SecurityGroup> findSGWithTx(Connection conn, Collection<Long> txList, String tenant, Pageable pageable) throws SQLException {
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
            stmt.setArray(++c, conn.createArrayOf("INTEGER", txList.toArray(new Long[0])));
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
                        ApplicationTransaction tx = new ApplicationTransaction();
                        tx.setTenant(tenant);
                        tx.setId(rs.getLong("tx"));

                        sg = new SecurityGroup();
                        sg.setId(sgId);
                        sg.setTenant(tenant);
                        sg.setTx(tx);
                        sgMap.put(sgId, sg);
                    }

                    long arId = rs.getLong("id");
                    if (arId != 0) {
                        var rights = rs.getString("rights");
                        if (Strings.CS.startsWith(rights, "1")) {
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

    public FilteredNodeSet findNodesWithTx(Connection conn, Collection<Long> txList, Set<String> includedUUIDs, Set<String> excludedUUIDs, Pageable pageable, boolean indexingDisabled, ReindexTable table) throws SQLException {
        try {
            final Map<Long, ActiveNode> nodeMap = new HashMap<>();
            var sql = """
                select n.id,n.tenant,n.uuid,n.type_name,n.data,n.updated_at,n.sg_id,n.tx,n.tx_flags,
                f.contentref,f.cached_text\s
                from (
                select n.id,n.tenant,n.uuid,n.type_name,n.data,n.updated_at,n.sg_id,n.tx,n.tx_flags from ecm_nodes n\s
                """;

            if (table.equals(ReindexTable.TX_NODES)) {
                sql += """
                    join ecm_transaction_nodes tn on (n.id = tn.node_id and tn.tx_id > n.tx)\s
                    where tn.tx_id = any (?)
                    """;
            } else if (table.equals(ReindexTable.PATHS)) {
                sql += """
                    join ecm_paths p on (n.id = p.node_id and p.tx > n.tx)\s
                    where p.tx = any (?)
                    """;
            } else {
                sql += "where n.tx = any (?)";
            }

            if (includedUUIDs != null) {
                sql += " and n.uuid = any (?)";
            }

            if (excludedUUIDs != null) {
                sql += " and not (n.uuid = any (?))";
            }

            if (pageable != null) {
                sql += " order by n.id limit ? offset ?";
            }

            sql += """
                ) n\s
                left outer join jsonb_array_elements(n.data->'contents') c on true\s
                left outer join ecm_files f on (
                """;

            var tenantIsolationEnabled = tenantRepository.findByIdOptional(sessionContext.getTenant())
                    .map(TenantSpace::getData)
                    .map(TenantData::getTenantIsolationEnabled)
                    .orElse(globalTenantIsolationEnabled);
            if (tenantIsolationEnabled) {
                sql += "f.tenant = n.tenant and ";
            }

            sql += "f.contentref = c->>'contentUrl')";

            int count = 0;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int c = 0;
                stmt.setArray(++c, conn.createArrayOf("INTEGER", txList.toArray(new Long[0])));

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
                    var txMap = new HashMap<Long,ApplicationTransaction>();
                    while (rs.next()) {
                        count++;
                        readActiveNode(rs, nodeMap, txMap, indexingDisabled);
                    }
                }
            }

            fillParents(conn, nodeMap);
            fillPaths(conn, nodeMap);

            return new FilteredNodeSet(nodeMap.values(), count);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public record FilteredNodeSet(Collection<ActiveNode> filteredNodes, int totalNodeCount) {}

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

    private void readActiveNode(ResultSet rs, Map<Long,ActiveNode> nodeMap, Map<Long,ApplicationTransaction> txMap, boolean indexingDisabled) throws SQLException, JsonProcessingException {
        var id = rs.getLong("id");
        var n = nodeMap.get(id);
        if (n == null) {
            n = new ActiveNode();
            n.setId(id);

            n.setTenant(rs.getString("tenant"));
            n.setUuid(rs.getString("uuid"));
            n.setTypeName(rs.getString("type_name"));

            NodeData data = objectMapper.readValue(rs.getString("data"), NodeData.class);
            sessionContext.getTenantData()
                .map(TenantData::getImplicitAspects)
                .filter(aspect -> !aspect.isEmpty())
                .ifPresent(aspects -> data.getAspects().addAll(aspects));

            if (indexingDisabled && !data.getAspects().contains(Constants.ASPECT_ECMSYS_INDEXING_REQUIRED)) {
                return;
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
                .filter(cp -> Strings.CS.equals(cp.getContentUrl(), contentUrl))
                .forEach(cp -> cp.setText(text));
        }
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
        final String sql = "select p.node_id,p.node_path,p.sg_path,p.file_path,p.lev,p.is_hard,p.tx " +
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

                        long txId = rs.getLong("tx");
                        if (txId > node.getTx().getId()) {
                            var tx = new ApplicationTransaction();
                            tx.setId(txId);
                            node.setTx(tx);
                        }

                        node.getPaths().add(p);
                    }
                }
            }
        }
    }
}

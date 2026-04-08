package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.domain.model.files.FileDescriptor;
import it.doqui.libra.librabl.domain.model.files.FileId;
import it.doqui.libra.librabl.domain.model.graph.IndexingFlags;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.policy.CopyMode;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.FileData;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.SecurityGroup;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static it.doqui.libra.librabl.domain.model.graph.IndexingFlags.formatAsBinary;

@ApplicationScoped
@Slf4j
public class NodeDAO extends AbstractDAO {

    @ConfigProperty(name = "libra.content-store.tenant-isolation", defaultValue = "true")
    boolean globalTenantIsolationEnabled;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TenantRepository tenantRepository;

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
                stmt.setString(4, sessionContext.getUserContext().getTenantRef().toString());
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
                insert into ecm_nodes (tenant,uuid,type_name,sg_id,tx,tx_flags,data,updated_at,code)\s
                values (?,?,?,?,?,?,?::jsonb,?,?)
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
                stmt.setString(9, node.getCode());

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
                type_name = ?, sg_id = ?, tx = ?, tx_flags = ?, data = ?::jsonb, updated_at = ?, code = ?\s
                where id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, node.getTypeName());
                DBUtils.setLong(stmt, 2, Optional.ofNullable(node.getSecurityGroup()).map(SecurityGroup::getId).orElse(null));
                DBUtils.setLong(stmt, 3, Optional.ofNullable(node.getTx()).map(ApplicationTransaction::getId).orElse(null));
                stmt.setString(4, node.getTransactionFlags());
                stmt.setString(5, objectMapper.writeValueAsString(node.getData()));
                stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                stmt.setString(7, node.getCode());
                stmt.setLong(8, node.getId());

                if (stmt.executeUpdate() < 1) {
                    throw new RuntimeException("Unable to update node");
                }

                setExternalProperties(conn, node);

                log.debug("Node {} updated", node.getId());
                return null;
            } catch (SQLException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void setExternalProperties(Connection conn, ActiveNode node) throws SQLException {
        if (!node.getExternalProperties().isEmpty()) {
            deleteExternalProperties(conn, node);
            updateExternalProperties(conn, node);
        }
    }

    private void deleteExternalProperties(Connection conn, ActiveNode node) throws SQLException {
        var nullProperties = node.getExternalProperties()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .toList();
        if (!nullProperties.isEmpty()) {
            var sql = "delete from ecm_external_properties where node_id = ? and prop_name = any (?)";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, node.getId());
                stmt.setArray(2, conn.createArrayOf("VARCHAR", nullProperties.toArray(new String[0])));
                stmt.executeUpdate();
            }
        }
    }

    private void updateExternalProperties(Connection conn, ActiveNode node) throws SQLException {
        var existsValuedProperty = node.getExternalProperties().entrySet().stream().anyMatch(entry -> entry.getValue() != null);
        if (existsValuedProperty) {
            var sql = """
                    insert into ecm_external_properties (node_id, prop_name, prop_value) values (?,?,?)\s
                    on conflict (node_id, prop_name) do update set prop_value = excluded.prop_value
                    """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, node.getId());
                for (var entry : node.getExternalProperties().entrySet()) {
                    var value = entry.getValue();
                    if (value != null) {
                        var s = value.toString().replaceAll("\\x00", "");
                        if (s.startsWith("\ufeff")) {
                            s = s.substring(1);
                        }

                        stmt.setString(2, entry.getKey());
                        stmt.setString(3, s);
                        stmt.executeUpdate();
                    }
                }
            }
        }
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
        var tenantIsolationEnabled = tenantRepository.findByIdOptional(sessionContext.getTenant())
                .map(TenantSpace::getData)
                .map(TenantData::getTenantIsolationEnabled)
                .orElse(globalTenantIsolationEnabled);
        call(conn -> {
            var sql = """
                update ecm_files set counter = counter + s.cnt\s
                from (
                    select jsonb_array_elements(data->'contents')->>'contentUrl' as content, count(*) as cnt\s
                    from ecm_nodes where tx = ? and data->'contents' is not null\s
                    group by content
                ) s\s
                where contentref = s.content and counter >= 0
                """;

            if (tenantIsolationEnabled) {
                sql += " and tenant = ?";
            }

            try (var stmt = conn.prepareStatement(sql))
            {
                int c = 0;
                stmt.setLong(++c, tx.getId());
                if (tenantIsolationEnabled) {
                    stmt.setString(++c, tx.getTenant());
                }

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
        decrementContentRef(tenant, List.of(() -> URI.create(contentUrl)));
    }

    public void decrementContentRef(ActiveNode node) {
        decrementContentRef(node.getTenant(), node.getData().getContents());
    }

    public void decrementContentRef(String tenant, List<? extends FileId> contents) {
        if (contents != null && !contents.isEmpty()) {
            call(conn -> {
                var sql = "update ecm_files set counter = counter - 1 where contentref = ? and counter > 0";
                var tenantIsolationEnabled = tenantRepository.findByIdOptional(tenant)
                        .map(TenantSpace::getData)
                        .map(TenantData::getTenantIsolationEnabled)
                        .orElse(globalTenantIsolationEnabled);
                if (tenantIsolationEnabled) {
                    sql += " and tenant = ?";
                }
                try (var stmt = conn.prepareStatement(sql)) {
                    for (var cp : contents) {
                        if (cp.getFileURI() != null) {
                            log.trace("Decrementing content counter of {} {}", tenant, cp.getFileURI());
                            int c = 0;
                            stmt.setString(++c, cp.getFileURI().toString());
                            if (tenantIsolationEnabled) {
                                stmt.setString(++c, tenant);
                            }
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

    public void incrementContentRef(String tenant, FileDescriptor fd) {
        incrementContentRef(tenant, List.of(fd));
    }

    public void incrementContentRef(ActiveNode node) {
        incrementContentRef(node.getTenant(), node.getData().getContents());
    }

    public void incrementContentRef(String tenant, Collection<? extends FileDescriptor> contents) {
        if (contents != null && !contents.isEmpty()) {
            call(conn -> {
                var sql = """
                    insert into ecm_files (tenant,contentref,contentsize,contenthash,counter) values (?,?,?,?,1)\s
                    on conflict on constraint ecm_files_pkey do update set\s
                    counter = ecm_files.counter + excluded.counter\s
                    where ecm_files.counter >= 0
                    """;

                try (var stmt = conn.prepareStatement(sql)) {
                    for (var cp : contents) {
                        if (cp.getFileURI() != null) {
                            log.trace("Incrementing content counter of {} (tenant {})", cp.getFileURI(), tenant);
                            updateFileStatement(stmt, cp, tenant);
                        }
                    }

                    return null;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public void unCountContentRef(String dbSchema, String tenant, FileDescriptor cp) {
        if (cp != null && cp.getFileURI() != null) {
            DBUtils.call(ds, dbSchema, conn -> {
                var sql = """
                    insert into ecm_files (tenant,contentref,contentsize,contenthash,counter) values (?,?,?,?,-1)\s
                    on conflict on constraint ecm_files_pkey do update set\s
                    counter = excluded.counter
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    log.trace("Setting -1 in content counter of {} (tenant {})", cp.getFileURI(), tenant);
                    updateFileStatement(stmt, cp, tenant);
                    return null;
                } catch (SQLException e) {
                    throw new SystemException(e);
                }
            });
        }
    }

    private void updateFileStatement(PreparedStatement stmt, FileDescriptor fd, String tenant) throws SQLException {
        int c = 0;
        if (tenant != null) {
            stmt.setString(++c, tenant);
        }

        stmt.setString(++c, fd.getFileURI().toString());
        stmt.setLong(++c, Optional.ofNullable(fd.getSize()).orElse(0L));
        stmt.setString(++c, fd.getHash());
        stmt.executeUpdate();
    }

    public Optional<String> findUUIDWherePath(String path) {
        if (StringUtils.isBlank(path)) {
            return Optional.empty();
        }

        var p = Strings.CS.endsWith(path, "/") ? path : path + "/";
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
                stmt.setString(1, sessionContext.getUserContext().getTenantRef().toString());
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



    public void createSG(SecurityGroup sg) {
        call(conn -> {
            try {
                try (var stmt = conn.prepareStatement("""
                    insert into ecm_security_groups (tenant,name,uuid,tx,inheritance,managed,updated_at)\s
                    values (?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS))
                {
                    stmt.setString(1, sg.getTenant());
                    stmt.setString(2, sg.getName());
                    stmt.setString(3, sg.getUuid());
                    stmt.setLong(4, sg.getTx().getId());
                    stmt.setBoolean(5, sg.isInheritanceEnabled());
                    stmt.setBoolean(6, sg.isManaged());
                    stmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));

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
                    insert into ecm_nodes (data, tenant, type_name, code, updated_at, uuid, sg_id, tx, tx_flags)\s
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
                        n.type_name, n.code, now(), gen_random_uuid(), n.sg_id, ?, ?\s
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
                    insert into ecm_security_groups (name, tenant, tx, updated_at, inheritance, managed, sg_src)\s
                    select s.name, s.tenant, ?, ?, s.inheritance, s.managed, n.sg_id\s
                    from ecm_security_groups s join ecm_nodes n on s.id = n.sg_id and s.managed\s
                    where n.tx = ? and n.data->'internals'->'ecm-sys:source-DBID' is not null\s
                    and n.id != ?
                    """))
                {
                    stmt.setLong(1, tx.getId());
                    stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    stmt.setLong(3, tx.getId());
                    stmt.setLong(4, target.getId());
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

    public String retrieveUUIDFromPath(String path) {
        return call(conn -> {
            try (var stmt = conn.prepareStatement("""
                select uuid
                from ecm_nodes n join ecm_paths p on n.id = p.node_id
                where file_path = ?
                """)) {
                stmt.setString(1, path.endsWith("/") ? path : path + "/");
                try (var resultSet = stmt.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("uuid");
                    }
                }
            } catch (SQLException e) {
                log.error("Error while retrieving path from node");
                throw new SystemException(e);
            }
            return null;
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

    public Map<String, String> mapContentsByHash(String tenant, Collection<String> hashes) {
        return call(conn -> {
            var sql = """
                select f.contentref, f.contenthash\s
                from ecm_files f\s
                where f.tenant = ? and f.contenthash = any (?)\s
                order by f.contentref asc\s
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenant);
                stmt.setArray(2, conn.createArrayOf("VARCHAR", hashes.toArray(new String[0])));
                try (var rs = stmt.executeQuery()) {
                    var map = new HashMap<String,String>();
                    while (rs.next()) {
                        map.putIfAbsent(rs.getString("contenthash"), rs.getString("contentref"));
                    }
                    return map;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Collection<FileDescriptor> findContentProperties(String tenant, Collection<String> contentUrls) {
        return call(conn -> {
            var sql = """
                select f.contentref, f.contenthash, f.contentsize\s
                from ecm_files f\s
                where f.tenant = ? and f.contentref = any (?)\s
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenant);
                stmt.setArray(2, conn.createArrayOf("VARCHAR", contentUrls.toArray(new String[0])));
                try (var rs = stmt.executeQuery()) {
                    var list = new ArrayList<FileDescriptor>();
                    while (rs.next()) {
                        var cp = new FileData();
                        cp.setContentUrl(rs.getString("contentref"));
                        cp.setSize(rs.getLong("contentsize"));
                        cp.setHash(rs.getString("contenthash"));
                        list.add(cp);
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

}

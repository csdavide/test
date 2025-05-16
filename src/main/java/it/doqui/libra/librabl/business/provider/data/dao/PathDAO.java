package it.doqui.libra.librabl.business.provider.data.dao;

import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.views.node.NodeInfoItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.SQLException;
import java.util.*;

@ApplicationScoped
public class PathDAO extends AbstractDAO {

    public int propagatePathsTransaction(long txId) {
        var sql = """
            update ecm_nodes set tx = ?\s
            from ecm_paths p\s
            where ecm_nodes.id = p.node_id and p.tx = ?
            """;
        return call(conn -> {
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, txId);
                stmt.setLong(2, txId);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Optional<NodeInfoItem> findNodeIdWherePath(String path) {
        if (StringUtils.isBlank(path)) {
            return Optional.empty();
        }

        var tenant = UserContextManager.getContext().getTenantRef().toString();
        var p = StringUtils.endsWith(path, "/") ? path : path + "/";
        return call(conn -> {
            final var sql = """
                select p.node_id,n.uuid,n.type_name\s
                from ecm_paths p\s
                join ecm_nodes n on (n.id = p.node_id)\s
                where n.tenant = ? and p.file_path = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenant);
                stmt.setString(2, p);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var n = new NodeInfoItem(
                            rs.getLong("node_id"),
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

    public Map<String, List<String>> pathOfUUIDs(Collection<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return Map.of();
        }

        return call(conn -> {
            final var sql = """
                select n.uuid,p.file_path\s
                from ecm_nodes n\s
                join ecm_paths p on (p.node_id = n.id)\s
                where n.tenant = ? and n.uuid = any (?)\s
                order by n.id asc,p.is_hard desc,p.id asc
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setArray(2, conn.createArrayOf("VARCHAR", uuids.toArray(new String[0])));
                try (var rs = stmt.executeQuery()) {
                    var resultMap = new HashMap<String, List<String>>();
                    while (rs.next()) {
                        var uuid = rs.getString("uuid");
                        var path = rs.getString("file_path");
                        if (StringUtils.endsWith(path, "/")) {
                            path = path.substring(0, path.length() - 1);
                        }

                        var finalPath = path;
                        resultMap.compute(uuid, (k, v) -> {
                            final List<String> result = v == null ? new ArrayList<>() : v;
                            result.add(finalPath);
                            return result;
                        });
                    }
                    return resultMap;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<String> findNodePathByFilePath(String filePath) {
        return call(conn -> {
            final var sql = """
                select p.node_path\s
                from ecm_nodes n\s
                join ecm_paths p on (p.node_id = n.id)\s
                where n.tenant = ? and p.file_path = ?\s
                limit 1
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setString(2, filePath);
                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString("node_path")) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<String> findNodeUUIDByFilePath(String filePath) {
        return call(conn -> {
            final var sql = """
                select n.uuid\s
                from ecm_nodes n\s
                join ecm_paths p on (p.node_id = n.id)\s
                where n.tenant = ? and p.file_path = ?\s
                limit 1
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setString(2, filePath);
                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString("uuid")) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<Pair<String,String>> findLongestPathNode(Collection<String> paths) {
        return call(conn -> {
            final var sql = """
                select p.file_path,n.uuid\s
                from ecm_nodes n\s
                join ecm_paths p on (p.node_id = n.id)\s
                where n.tenant = ? and p.file_path = any (?)
                order by p.lev desc\s
                limit 1
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, UserContextManager.getTenant());
                stmt.setArray(2, conn.createArrayOf("VARCHAR", paths.toArray(new String[0])));
                try (var rs = stmt.executeQuery()) {
                    return rs.next()
                        ? Optional.of(new ImmutablePair<>(rs.getString("file_path"), rs.getString("uuid")))
                        : Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }
}

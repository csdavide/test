package it.doqui.libra.librabl.business.provider.data.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.NodeData;
import it.doqui.libra.librabl.business.provider.data.entities.VersionDetails;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.version.VersionItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class VersionDAO extends AbstractDAO {

    @Inject
    NodeDAO nodeDAO;

    @Inject
    ObjectMapper objectMapper;

    public int createNodeVersions(ApplicationTransaction tx) {
        return call(conn -> {
            var sql = """
                insert into ecm_versions (version_uuid,node_id,version,tag,data,created_at,created_by)\s
                select gen_random_uuid(),n.id,n.version,null,n.data,?,?\s
                from ecm_nodes n\s
                where n.tx = ?\s
                and n.version = 0\s
                and (
                (n.data->'properties'->>'cm:initialVersion')::bool\s
                or (n.data->'properties'->>'cm:autoVersion')::bool
                )\s
                on conflict (node_id,version) do nothing
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                stmt.setString(2, UserContextManager.getContext().getAuthority());
                stmt.setLong(3, tx.getId());
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<VersionItem> createNodeVersion(ActiveNode node, String tag) {
        return call(conn -> {
            var sql = """
                insert into ecm_versions (version_uuid,node_id,version,tag,data,created_at,created_by)\s
                select ?,n.id,n.version,?,n.data,?,?\s
                from ecm_nodes n\s
                where n.id = ?\s
                on conflict (node_id,version) do nothing\s
                returning version
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                var item = new VersionItem();
                item.setNodeId(node.getId());
                item.setNodeUUID(node.getUuid());
                item.setVersionUUID(UUID.randomUUID().toString());
                item.setVersionTag(tag);
                item.setCreatedAt(ZonedDateTime.now());
                item.setCreatedBy(UserContextManager.getContext().getAuthority());

                stmt.setString(1, item.getVersionUUID());
                stmt.setString(2, tag);
                stmt.setTimestamp(3, new Timestamp(item.getCreatedAt().toInstant().toEpochMilli()));
                stmt.setString(4, item.getCreatedBy());
                stmt.setLong(5, node.getId());

                if (!stmt.execute()) {
                    return Optional.empty();
                }

                try (var rs = stmt.getResultSet()) {
                    if (rs.next()) {
                        item.setVersion(rs.getInt("version"));
                    } else {
                        return Optional.empty();
                    }
                }

                nodeDAO.incrementContentRef(node);
                return Optional.of(item);
            } catch (SQLException e) {
                if (StringUtils.contains(e.getMessage(), "already exists")) {
                    throw new BadRequestException(e.getMessage());
                }

                throw new SystemException(e);
            }
        });
    }

    public List<VersionItem> listNodeVersions(long nodeId, List<String> tags) {
        return call(conn -> {
            var sql = "select version_uuid,node_id,version,tag,created_at,created_by from ecm_versions where node_id = ?";
            if (tags != null && !tags.isEmpty()) {
                sql += " and tag like any (?)";
            }

            sql += " order by node_id,version";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, nodeId);
                if (tags != null && !tags.isEmpty()) {
                    var array = tags.stream()
                        .filter(StringUtils::isNotBlank)
                        .map(s -> {
                            s = s.replace("*", "%");
                            if (!s.contains("%")) {
                                s += "%";
                            }

                            return s;
                        })
                        .toList()
                        .toArray(new String[0]);
                    stmt.setArray(2, conn.createArrayOf("VARCHAR", array));
                }

                try (var rs = stmt.executeQuery()) {
                    var versions = new ArrayList<VersionItem>();
                    while (rs.next()) {
                        versions.add(readVersion(rs));
                    }
                    return versions;
                }

            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<VersionDetails> getNodeVersion(long nodeId, int version) {
        return call(conn -> {
            var sql = """
                select version_uuid,node_id,version,tag,created_at,created_by,data\s
                from ecm_versions\s
                where node_id = ? and version = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, nodeId);
                stmt.setInt(2, version);
                return getNodeVersion(stmt);
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public Optional<VersionDetails> getNodeVersion(String versionUUID) {
        return call(conn -> {
            var sql = """
                select version_uuid,node_id,version,tag,created_at,created_by,data\s
                from ecm_versions\s
                where version_uuid = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, versionUUID);
                return getNodeVersion(stmt);
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private Optional<VersionDetails> getNodeVersion(PreparedStatement stmt) throws SQLException {
        try (var rs = stmt.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }

            var result = new VersionDetails();
            result.setItem(readVersion(rs));

            try {
                var dataString = rs.getString("data");
                if (dataString != null) {
                    result.setData(objectMapper.readValue(dataString, NodeData.class));
                }
            } catch (JsonProcessingException e) {
                throw new SystemException(e);
            }

            return Optional.of(result);
        }
    }

    public boolean alterTagVersion(long nodeId, int version, String tag) {
        return call(conn -> {
            var sql = """
                update ecm_versions set tag = ?\s
                where node_id = ? and version = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tag);
                stmt.setLong(2, nodeId);
                stmt.setInt(3, version);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private VersionItem readVersion(ResultSet rs) throws SQLException {
        var item = new VersionItem();
        item.setVersionUUID(rs.getString("version_uuid"));
        item.setNodeId(rs.getLong("node_id"));
        item.setVersion(rs.getInt("version"));
        item.setVersionTag(rs.getString("tag"));
        item.setCreatedAt(DBUtils.getZonedDateTime(rs, "created_at"));
        item.setCreatedBy(rs.getString("created_by"));
        return item;
    }
}

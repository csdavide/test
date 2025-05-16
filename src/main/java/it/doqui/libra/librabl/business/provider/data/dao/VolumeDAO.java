package it.doqui.libra.librabl.business.provider.data.dao;

import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import it.doqui.libra.librabl.views.management.VolumeInfo;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;

@ApplicationScoped
public class VolumeDAO extends AbstractDAO {

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    public Collection<VolumeInfo> getVolumes() {
        return DBUtils.call(ds, masterSchema, conn -> {
            try {
                var now = ZonedDateTime.now();
                final var volumeMap = new HashMap<String, VolumeInfo>();
                for (var schema : listAvailableSchemas(conn)) {
                    conn.setSchema(schema);
                    calculateVolumes(conn, volumeMap);
                    calculateNodes(conn, volumeMap);
                    calculateArchivedNodes(conn, volumeMap);
                }
                return volumeMap.values()
                    .stream()
                    .peek(v -> v.setTimestamp(now))
                    .toList();
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private List<String> listAvailableSchemas(Connection conn) throws SQLException {
        try (var stmt = conn.prepareStatement("select distinct schema_name from ecm_tenants where root_id is not null")) {
            try (var rs = stmt.executeQuery()) {
                var result = new ArrayList<String>();
                while (rs.next()) {
                    result.add(rs.getString("schema_name"));
                }
                return result;
            }
        }
    }

    private void calculateVolumes(Connection conn, Map<String, VolumeInfo> volumeMap) throws SQLException {
        var sql = """
            select f.tenant,count(*) num_files,sum(f.contentsize) totalsize\s
            from ecm_files f\s
            group by f.tenant
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var item = getVolumeInfo(rs.getString("tenant"), volumeMap);
                    item.setFileCount(rs.getLong("num_files"));
                    item.setSize(rs.getLong("totalsize"));
                }
            }
        }
    }

    private void calculateNodes(Connection conn, Map<String, VolumeInfo> volumeMap) throws SQLException {
        var sql = """
            select n.tenant,count(*) num_nodes,sum(coalesce(jsonb_array_length(n.data->'contents'),0)) num_contents\s
            from ecm_nodes n\s
            group by n.tenant;
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var item = getVolumeInfo(rs.getString("tenant"), volumeMap);
                    item.setNodeCount(rs.getLong("num_nodes"));
                    item.setContentCount(rs.getLong("num_contents"));
                }
            }
        }
    }

    private void calculateArchivedNodes(Connection conn, Map<String, VolumeInfo> volumeMap) throws SQLException {
        var sql = """
            select n.tenant,count(*) num_nodes,sum(coalesce(jsonb_array_length(n.data->'contents'),0)) num_contents\s
            from ecm_archived_nodes n\s
            group by n.tenant;
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var item = getVolumeInfo(rs.getString("tenant"), volumeMap);
                    item.setArchivedNodeCount(rs.getLong("num_nodes"));
                    item.setArchivedContentCount(rs.getLong("num_contents"));
                }
            }
        }
    }

    private VolumeInfo getVolumeInfo(String tenant, Map<String, VolumeInfo> volumeMap) {
        var item = volumeMap.get(tenant);
        if (item == null) {
            item = new VolumeInfo();
            item.setTenant(tenant);
            volumeMap.put(tenant, item);
        }

        return item;
    }
}

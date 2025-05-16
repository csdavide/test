package it.doqui.libra.librabl.business.provider.data.dao;

import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.Pageable;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.function.ToIntFunction;

@Slf4j
public abstract class AbstractDAO {

    @ConfigProperty(name = "libra.delete.limit", defaultValue = "10000")
    protected int deleteLimit;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    protected AgroalDataSource ds;

    protected <R> R call(Function<Connection,R> f) {
        return DBUtils.call(ds, UserContextManager.getContext().getDbSchema(), f);
    }

    protected ApplicationTransaction readTx(ResultSet rs) throws SQLException {
        var tx = new ApplicationTransaction();
        tx.setId(rs.getLong("id"));
        tx.setTenant(rs.getString("tenant"));
        tx.setUuid(rs.getString("uuid"));
        tx.setCreatedAt(DBUtils.getZonedDateTime(rs, "created_at"));
        tx.setIndexedAt(DBUtils.getZonedDateTime(rs, "indexed_at"));
        return tx;
    }

    protected String formatSQL(final String sql, final String fields, final String order, Pageable pageable, boolean counting) {
        var result = sql;
        if (!counting) {
            result += " order by " + order;

            if (pageable != null) {
                result += " offset ? limit ?";
            }
        }

        return result.replace("{fields}", counting ? "count(*)" : fields);
    }

    protected <T> Paged<T> find(Connection conn, final String sql, final String fields, final String order, Pageable pageable, ToIntFunction<PreparedStatement> setParam, Function<ResultSet,T> reader) throws SQLException {
        var totalElements = 0;
        if (pageable != null) {
            //noinspection SqlSourceToSinkFlow
            try (var stmt = conn.prepareStatement(formatSQL(sql, fields, order, pageable, true))) {
                setParam.applyAsInt(stmt);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalElements = rs.getInt(1);
                    }
                }
            }
        }

        var items = new LinkedList<T>();
        //noinspection SqlSourceToSinkFlow
        try (var stmt = conn.prepareStatement(formatSQL(sql, fields, order, pageable, false))) {
            var c = setParam.applyAsInt(stmt);
            if (pageable != null) {
                stmt.setInt(++c, pageable.getPage() * pageable.getSize());
                stmt.setInt(++c, pageable.getSize());
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var item = reader.apply(rs);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }

        if (pageable == null) {
            return new Paged<>(items);
        } else {
            return new Paged<>(pageable.getPage(), pageable.getSize(), totalElements, items);
        }
    }

    protected Long[] listArchivedNodes(Connection conn, long txId) throws SQLException {
        try (var stmt = conn.prepareStatement("select id from ecm_archived_nodes where tx = ?")) {
            return listDeletedNodes(stmt, txId);
        }
    }

    protected Long[] listPurgedNodes(Connection conn, long txId) throws SQLException {
        try (var stmt = conn.prepareStatement("select id from ecm_removed_nodes where tx = ?")) {
            return listDeletedNodes(stmt, txId);
        }
    }

    private Long[] listDeletedNodes(PreparedStatement stmt, long txId) throws SQLException {
        stmt.setLong(1, txId);
        try (var rs = stmt.executeQuery()) {
            var archivedNodes = new ArrayList<Long>();
            while (rs.next()) {
                archivedNodes.add(rs.getLong("id"));
            }

            return archivedNodes.toArray(new Long[0]);
        }
    }

    protected void purgeNodeVersions(Connection conn, long txId) throws SQLException {
        var sql = """
            delete from ecm_versions\s
            where node_id in (select id from ecm_removed_nodes where tx = ?)
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, txId);
            stmt.executeUpdate();
        }
    }

    protected void purgeNodeVersions(Connection conn, Long[] archivedNodesArray) throws SQLException {
        try (var stmt = conn.prepareStatement("delete from ecm_versions where node_id = any (?)")) {
            stmt.setArray(1, conn.createArrayOf("INTEGER", archivedNodesArray));
            stmt.executeUpdate();
        }
    }

    protected void cleanSecurityGroups(Connection conn, long txId) throws SQLException {
        try (var stmt = conn.prepareStatement("""
            update ecm_security_groups set tx = ?\s
            where managed and id in (select sg_id from ecm_removed_nodes where tx = ?)
            """)) {
            stmt.setLong(1, txId);
            stmt.setLong(2, txId);
            int n = stmt.executeUpdate();
            log.debug("{} SGs of removed nodes updated", n);
        } catch (SQLException e) {
            log.error("Cannot updated security groups");
            throw e;
        }

        try (var stmt = conn.prepareStatement("""
                            delete from ecm_access_rules\s
                            where sg_id in (select id from ecm_security_groups where managed and tx = ?)
                            """))
        {
            stmt.setLong(1, txId);
            int n = stmt.executeUpdate();
            log.debug("{} access rules of SG deleted", n);
        } catch (SQLException e) {
            log.error("Cannot delete access rules");
            throw e;
        }
    }
}

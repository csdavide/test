package it.doqui.libra.librabl.utils;

import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public class DBUtils {

    private DBUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
        boolean value = rs.getBoolean(columnName);
        return rs.wasNull() ? null : value;
    }

    public static Byte getByte(ResultSet rs, String columnName) throws SQLException {
        byte value = rs.getByte(columnName);
        return rs.wasNull() ? null : value;
    }

    public static Long getLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    public static Integer getInt(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    public static Short getShort(ResultSet rs, String columnName) throws SQLException {
        short value = rs.getShort(columnName);
        return rs.wasNull() ? null : value;
    }

    public static Float getFloat(ResultSet rs, String columnName) throws SQLException {
        float value = rs.getFloat(columnName);
        return rs.wasNull() ? null : value;
    }

    public static Double getDouble(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getFloat(columnName);
        return rs.wasNull() ? null : value;
    }

    public static ZonedDateTime getZonedDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts == null ? null : ts.toInstant().atZone(ZoneId.systemDefault());
    }

    public static void doInTransaction(DataSource ds, String schema, Consumer<Connection> consumer) {
        doInSchema(ds, schema, conn -> {
            try {
                conn.setAutoCommit(false);
                doInTransaction(conn, consumer);
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private static void doInTransaction(Connection conn, Consumer<Connection> consumer) throws SQLException {
        try {
            consumer.accept(conn);
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        }
    }

    public static void doInSchema(DataSource ds, String schema, Consumer<Connection> consumer) {
        try (var conn = ds.getConnection()) {
            var prevSchema = conn.getSchema();
            if (schema != null) {
                conn.setSchema(schema);
            }

            try {
                consumer.accept(conn);
            } finally {
                if (prevSchema != null) {
                    conn.setSchema(prevSchema);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public static <R> R transactionCall(DataSource ds, String schema, Function<Connection,R> f) {
        return call(ds, schema, conn -> {
            try {
                conn.setAutoCommit(false);
                return transactionCall(conn, f);
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    public static <R> R transactionCall(DataSource ds, List<String> schemas, Function<Connection,R> f) {
        return call(ds, schemas, conn -> {
            try {
                conn.setAutoCommit(false);
                return transactionCall(conn, f);
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private static <R> R transactionCall(Connection conn, Function<Connection,R> f) throws SQLException {
        try {
            R r = f.apply(conn);
            conn.commit();
            return r;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        }
    }

    public static <R> R call(DataSource ds, List<String> schemas, Function<Connection,R> f) {
        try (var conn = ds.getConnection()) {
            if (schemas != null && !schemas.isEmpty()) {
                if (schemas.size() == 1) {
                    if (!StringUtils.equals(conn.getSchema(), schemas.get(0))) {
                        conn.setSchema(schemas.get(0));
                    }
                } else {
                    log.debug("Setting multiple schema {}", String.join(",", schemas));
                    try (var stmt = conn.prepareStatement("select set_config('search_path', ?, false)")) {
                        stmt.setString(1, String.join(",", schemas));
                        stmt.execute();
                    }
                }
            }

            return f.apply(conn);
        } catch (SQLException e) {
            throw new SystemException(e);
        }
    }

    public static <R> R call(DataSource ds, String schema, Function<Connection,R> f) {
        try (var conn = ds.getConnection()) {
            if (schema != null && !StringUtils.equals(conn.getSchema(), schema)) {
                conn.setSchema(schema);
            }

            return f.apply(conn);
        } catch (SQLException e) {
            throw new SystemException(e);
        }
    }

    public static void setLong(PreparedStatement stmt, int position, Long value) throws SQLException {
        if (value != null) {
            stmt.setLong(position, value);
        } else {
            stmt.setNull(position, Types.BIGINT);
        }
    }

}

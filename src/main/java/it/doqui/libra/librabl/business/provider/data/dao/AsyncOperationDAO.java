package it.doqui.libra.librabl.business.provider.data.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.provider.data.entities.AsyncOperationEntity;
import it.doqui.libra.librabl.foundation.async.AsyncOperation;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class AsyncOperationDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    public Optional<AsyncOperationEntity> findByIdOptional(String id) {
        return call(conn -> {
            var sql = """
                select id,tenant,status,data,created_at,updated_at\s
                from ecm_async_operations\s
                where id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(read(rs));
                    }

                    return Optional.empty();
                }
            } catch (SQLException | JsonProcessingException e) {
                throw new SystemException(e);
            }
        });
    }

    public void persist(AsyncOperationEntity entity) {
        call(conn -> {
            var sql = """
                insert into ecm_async_operations (id,tenant,status,data,created_at,updated_at)\s
                values (?,?,?,?::jsonb,?,?)\s
                on conflict (id) do update set\s
                status = excluded.status,
                data = excluded.data,
                updated_at = excluded.updated_at
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                var nowTS = new Timestamp(System.currentTimeMillis());
                stmt.setString(1, entity.getId());
                stmt.setString(2, entity.getTenant());
                stmt.setString(3, Objects.toString(entity.getStatus()));
                stmt.setString(4, objectMapper.writeValueAsString(entity.getData()));
                stmt.setTimestamp(5, nowTS);
                stmt.setTimestamp(6, nowTS);
                stmt.executeUpdate();
            } catch (SQLException | JsonProcessingException e) {
                throw new SystemException(e);
            }

            return null;
        });
    }

    public boolean deleteById(String id) {
        return call(conn -> {
            try (var stmt = conn.prepareStatement("delete from ecm_async_operations where id = ?")) {
                stmt.setString(1, id);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    private AsyncOperationEntity read(ResultSet rs) throws SQLException, JsonProcessingException {
        var result = new AsyncOperationEntity();
        result.setId(rs.getString("id"));
        result.setTenant(rs.getString("tenant"));
        result.setStatus(AsyncOperation.Status.valueOf(rs.getString("status")));

        var dataString = rs.getString("data");
        if (dataString != null) {
            TypeReference<HashMap<String,Object>> typeRef = new TypeReference<>() {};
            result.getData().putAll(objectMapper.readValue(dataString, typeRef));
        }

        result.setCreatedAt(DBUtils.getZonedDateTime(rs, "created_at"));
        result.setUpdatedAt(DBUtils.getZonedDateTime(rs, "updated_at"));

        return result;
    }
}

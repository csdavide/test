package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.application.model.jobs.responses.JobStatus;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.JobData;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.JobEntity;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@ApplicationScoped
@Slf4j
public class JobDAO extends AbstractDAO {

    @Inject
    ObjectMapper objectMapper;

    public JobEntity findById(String id, String schema, String tenant) {
        return call(schema, conn -> {
            var sql = """
                select id,tenant,status,data,created_at,updated_at\s
                from ecm_async_operations\s
                where id = ?\s
                """;

            if (tenant != null) {
                sql += " and tenant = ?";
            }

            try (var stmt = conn.prepareStatement(sql)) {
                int i = 0;
                stmt.setString(++i, id);
                if (tenant != null) {
                    stmt.setString(++i, tenant);
                }

                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return read(rs);
                    }

                    return null;
                }
            } catch (SQLException | JsonProcessingException e) {
                throw new SystemException(e);
            }
        });
    }

    public void persist(JobEntity entity) {
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
                stmt.setString(1, entity.getJobId());
                stmt.setString(2, entity.getTenant());
                stmt.setString(3, entity.getStatus().name());
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

    private JobEntity read(ResultSet rs) throws SQLException, JsonProcessingException {
        var entity = new JobEntity();
        entity.setJobId(rs.getString("id"));
        entity.setTenant(rs.getString("tenant"));
        entity.setCreatedAt(DBUtils.getZonedDateTime(rs, "created_at"));
        entity.setUpdatedAt(DBUtils.getZonedDateTime(rs, "updated_at"));

        var statusString = rs.getString("status");
        if (statusString == null) {
            throw new IllegalStateException("Missing status");
        }

        entity.setStatus(JobStatus.valueOf(statusString));

        var dataString = rs.getString("data");
        if (dataString != null) {
            entity.setData(objectMapper.readValue(dataString, JobData.class));
        } else {
            entity.setData(new JobData());
        }

        return entity;
    }
}

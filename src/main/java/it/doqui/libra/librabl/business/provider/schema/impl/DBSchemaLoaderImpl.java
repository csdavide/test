package it.doqui.libra.librabl.business.provider.schema.impl;

import io.agroal.api.AgroalDataSource;
import it.doqui.libra.librabl.business.service.schema.ModelItem;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.DBUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class DBSchemaLoaderImpl implements SchemaLoader {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    AgroalDataSource ds;

    @Override
    public List<ModelItem> loadSchema(String tenant, String dbSchema, boolean activeOnly) {
        log.debug("Loading models of tenant {} from schema {}", tenant, dbSchema);
        return DBUtils.call(ds, dbSchema, conn -> {
            final var sql = "select model_name,data,fmt,is_active from ecm_models " +
                "where tenant = ? and data is not null " +
                (activeOnly ? "and is_active " : "") +
                "order by id";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenant);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<ModelItem> models = new LinkedList<>();
                    while (rs.next()) {
                        ModelItem m = new ModelItem();
                        m.setName(rs.getString("model_name"));
                        m.setData(rs.getString("data"));
                        m.setFormat(rs.getString("fmt"));
                        m.setActive(rs.getBoolean("is_active"));
                        m.setTenant(tenant);
                        models.add(m);
                    }
                    return models;
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public Optional<ModelItem> getModel(TenantRef tenantRef, String dbSchema, String modelName) {
        log.debug("Looking for model {} of tenant {} into schema {}", modelName, tenantRef, dbSchema);
        return DBUtils.call(ds, dbSchema, conn -> {
            final var tenant  = tenantRef.toString();
            final var sql = "select model_name,data,fmt,is_active from ecm_models " +
                "where (tenant = ? or tenant = 'ANY') and model_name = ?";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenant);
                stmt.setString(2, modelName);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        var m = new ModelItem();
                        m.setName(rs.getString("model_name"));
                        m.setData(rs.getString("data"));
                        m.setFormat(rs.getString("fmt"));
                        m.setActive(rs.getBoolean("is_active"));
                        m.setTenant(tenant);
                        return Optional.of(m);
                    }

                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void saveModel(TenantRef tenantRef, String dbSchema, ModelItem m) {
        DBUtils.transactionCall(ds, dbSchema, conn -> {
            try {
                var sql = "insert into ecm_models (tenant,model_name,data,fmt,is_active) " +
                    "values (?,?,?,?,?) on conflict (tenant, model_name) do update set " +
                    "data = excluded.data," +
                    "fmt = excluded.fmt," +
                    "is_active = excluded.is_active";
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, m.getTenant());
                    stmt.setString(2, m.getName());
                    stmt.setString(3, m.getData());
                    stmt.setString(4, m.getFormat());
                    stmt.setBoolean(5, m.isActive());
                    stmt.executeUpdate();
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }

    @Override
    public void deleteModel(TenantRef tenantRef, String dbSchema, String modelName) {
        DBUtils.transactionCall(ds, dbSchema, conn -> {
            try {
                var sql = "delete from ecm_models where tenant = ? and model_name = ?";
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, tenantRef.toString());
                    stmt.setString(2, modelName);
                    stmt.executeUpdate();
                }

                return null;
            } catch (SQLException e) {
                throw new SystemException(e);
            }
        });
    }
}

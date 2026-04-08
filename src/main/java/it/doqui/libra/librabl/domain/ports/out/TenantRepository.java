package it.doqui.libra.librabl.domain.ports.out;

import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TenantRepository {
    void cleanCache();
    void removeTenantFromCache(String tenant);
    List<String> listAllSchemas();
    List<String> listTenantsInSchema(String schemaName);
    void checkIfSchemaExists(String schema);
    void initializeSchema(String schema, boolean includeFK);
    void destroySchema(String schema);
    void upgradeSchema(Set<String> schemaSet, int fromVersion, int targetVersion);
    void deleteTenant(String tenant);
    void cleanTenant(String tenant, String schema);
    void persist(TenantSpace t);
    void lockTenantTable();
    List<TenantSpace> findAll();
    List<TenantSpace> findStartingWith(String prefix);
    Optional<TenantSpace> findByIdOptional(String name);
}

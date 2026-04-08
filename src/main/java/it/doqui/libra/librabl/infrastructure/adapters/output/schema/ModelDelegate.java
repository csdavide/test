package it.doqui.libra.librabl.infrastructure.adapters.output.schema;

import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.foundation.TenantRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ModelDelegate {

    @Inject
    SchemaManager schemaManager;

    @Inject
    TenantRepository tenantRepository;

    public ModelSchema findSchema(String tenant) {
        TenantSchema schema = schemaManager.getTenantSchema(tenant);
        if (schema == null) {
            schema = tenantRepository
                .findByIdOptional(tenant)
                .map(t -> schemaManager.loadTenant(TenantRef.valueOf(t.getTenant()), t.getSchema()))
                .orElseThrow(() -> new RuntimeException(String.format("Tenant '%s' not found", tenant)));
        }

        SchemaChain chain = new SchemaChain();
        chain.getSchemas().add(schema);

        schema = schemaManager.getTenantSchema(TenantSchema.COMMON_SCHEMA);
        if (schema == null) {
            throw new RuntimeException("Common schema not found");
        }
        chain.getSchemas().add(schema);
        return chain;
    }
}

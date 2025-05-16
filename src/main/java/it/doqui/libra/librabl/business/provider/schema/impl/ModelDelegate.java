package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import lombok.extern.slf4j.Slf4j;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Slf4j
public class ModelDelegate {

    @Inject
    SchemaManager schemaManager;

    @Inject
    TenantDataManager tenantManager;

    public ModelSchema findSchema(String tenant) {
        TenantSchema schema = schemaManager.getTenantSchema(tenant);
        if (schema == null) {
            schema = tenantManager
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

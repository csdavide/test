package it.doqui.libra.librabl.infrastructure.adapters.output.schema;

import it.doqui.libra.librabl.domain.model.schema.ModelItem;
import it.doqui.libra.librabl.foundation.TenantRef;

import java.util.List;
import java.util.Optional;

public interface SchemaLoader {
    List<ModelItem> loadSchema(String tenant, String dbSchema, boolean activeOnly);
    Optional<ModelItem> getModel(TenantRef tenantRef, String dbSchema, String modelName);
    void saveModel(TenantRef tenantRef, String dbSchema, ModelItem m);
    void deleteModel(TenantRef tenantRef, String dbSchema, String modelName);
}

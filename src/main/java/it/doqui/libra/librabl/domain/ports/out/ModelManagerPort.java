package it.doqui.libra.librabl.domain.ports.out;

import it.doqui.libra.librabl.domain.model.schema.ModelItem;
import it.doqui.libra.librabl.domain.model.schema.AspectDescriptor;
import it.doqui.libra.librabl.domain.model.schema.CustomModelSchema;
import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.domain.model.schema.TypeDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;

public interface ModelManagerPort {
    void reloadTenant(String tenant);
    ModelSchema getContextModel();
    Collection<ModelItem> listStoredModels(boolean includeAny);
    Optional<ModelItem> getStoredModel(String modelName);
    TypeDescriptor getFlatType(String name);
    AspectDescriptor getFlatAspect(String name);
    Collection<CustomModelSchema> listModels(boolean includeAny);
    Optional<CustomModelSchema> getModel(String modelName);
    Optional<CustomModelSchema> getNamespaceSchema(String name);
    void deployModel(String fmt, InputStream is) throws IOException;
    void undeployModel(String modelName);
}

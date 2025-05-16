package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.views.schema.AspectDescriptor;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import it.doqui.libra.librabl.business.service.schema.ModelItem;
import it.doqui.libra.librabl.views.schema.TypeDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;

public interface ModelService {
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

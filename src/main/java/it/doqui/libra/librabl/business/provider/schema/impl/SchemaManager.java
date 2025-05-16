package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.schema.ModelItem;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import it.doqui.libra.librabl.views.schema.ModelNamespace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.schema.impl.TenantSchema.COMMON_SCHEMA;

@ApplicationScoped
@Slf4j
public class SchemaManager {

    private final Map<String, TenantSchema> tenants = new ConcurrentHashMap<>();

    @ConfigProperty(name = "libra.boot.schema-validation", defaultValue = "false")
    boolean validationOnLoad;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @ConfigProperty(name = "libra.multitenant.custom-models-on-common", defaultValue = "false")
    boolean supportCustomModelOnCommonSchema;

    @Inject
    SchemaLoader schemaLoader;

    @Inject
    SchemaProcessor schemaProcessor;

    public void createTenantSchemaIfRequired(String tenant, String schema) {
        var t = loadTenant(TenantRef.valueOf(tenant), schema);
        tenants.putIfAbsent(tenant, t);
    }

    public TenantSchema getTenantSchema(String tenant) {
        return tenants.get(tenant);
    }

    public void setTenantSchema(String tenant, TenantSchema schema) {
        tenants.put(tenant, schema);
    }

    public Set<String> getTenantNames() {
        return tenants.keySet();
    }

    public void validate(TenantRef tenantRef, ModelItem m) {
        log.debug("Processing model {}", m);
        var tenantSchema = getTenantSchema(tenantRef.toString());
        var commonSchema = getTenantSchema(COMMON_SCHEMA);
        var model = schemaProcessor.process(m, commonSchema, tenantSchema);
        if (model == null) {
            throw new BadRequestException("Unable to parse model");
        }

        for (var ns : model.getNamespaces()) {
            if (StringUtils.isNotBlank(ns.getPrefix())) {
                if (commonSchema.getNamespaces().get(ns.getPrefix()) != null) {
                    throw new ForbiddenException("A common namespace cannot be redefined: " + ns.getPrefix());
                }

                var other = tenantSchema.getNamespaces().get(ns.getPrefix());
                if (other != null && !StringUtils.equals(other.getModelName(), model.getModelName())) {
                    throw new ForbiddenException(
                        String.format("The namespace '%s' is already defined in the model %s ",
                            ns.getPrefix(), other.getModelName()));
                }
            }
        }

        if (!schemaProcessor.validate(tenantSchema, commonSchema, model)) {
            throw new BadRequestException("Invalid model");
        }

        m.setName(model.getModelName());
        m.setActive(true);
    }

    public Collection<ModelItem> loadSchema(TenantRef tenantRef, String dbSchema, boolean activeOnly, boolean includeAny) {
        var modelMap = new TreeMap<String, ModelItem>();
        schemaLoader
            .loadSchema(tenantRef.toString(), dbSchema, activeOnly)
            .forEach(m -> modelMap.put(m.getName(), m));

        if (includeAny) {
            schemaLoader
                .loadSchema(COMMON_SCHEMA, masterSchema, activeOnly)
                .forEach(m -> modelMap.putIfAbsent(m.getName(), m));
        }

        return modelMap.values();
    }

    public TenantSchema loadTenant(TenantRef tenantRef, String dbSchema) {
        return loadTenant(tenantRef, dbSchema, true);
    }

    public TenantSchema loadCommonSchema() {
        TenantSchema commonSchema = new TenantSchema();
        commonSchema.setTenant(COMMON_SCHEMA);
        //TODO: completare
        loadSchema(TenantRef.valueOf(COMMON_SCHEMA), masterSchema, true, false)
            .forEach(m -> {
                CustomModelSchema model = schemaProcessor.process(m, commonSchema, null);
                if (model != null) {
                    schemaProcessor.register(commonSchema, model);
                }
            });

        setTenantSchema(COMMON_SCHEMA, commonSchema);
        return commonSchema;
    }

    public TenantSchema loadTenant(TenantRef tenantRef, String dbSchema, boolean includeAny) {
        TenantSchema commonSchema = getTenantSchema(COMMON_SCHEMA);
        TenantSchema tenantSchema = new TenantSchema();
        tenantSchema.setTenant(tenantRef.toString());
        loadSchema(tenantRef, dbSchema, true, includeAny)
            .forEach(m -> {
                CustomModelSchema model = schemaProcessor.process(m, commonSchema, tenantSchema);
                if (model != null) {
                    schemaProcessor.register(tenantSchema, model);
                }
            });

        if (validationOnLoad) {
            log.info("Validating tenant {}", tenantRef);
            var notValidatedNamespaces = tenantSchema.getNamespaces().values()
                .stream()
                .filter(model -> {
                    try {
                        return !schemaProcessor.validate(tenantSchema, commonSchema, model);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        return true;
                    }
                })
                .flatMap(model -> model.getNamespaces().stream())
                .map(ModelNamespace::getPrefix)
                .peek(ns -> log.error("Invalid namespace {}", ns))
                .collect(Collectors.toSet());

            if (!notValidatedNamespaces.isEmpty()) {
                log.warn("Unregistering invalid namespaces '{}' from schema of tenant {}", notValidatedNamespaces, tenantRef);
                notValidatedNamespaces
                    .forEach(ns -> schemaProcessor.unregister(tenantSchema, ns));
            }
        }

        setTenantSchema(tenantSchema.getTenant(), tenantSchema);
        return tenantSchema;
    }

    public void undeploy(TenantSchema schema, String dbSchema, CustomModelSchema model) {
        model.getNamespaces().forEach(ns -> schemaProcessor.unregister(schema, ns.getPrefix()));
        schemaLoader.deleteModel(TenantRef.valueOf(schema.getTenant()), dbSchema, model.getModelName());
    }

    public Optional<ModelItem> retrieveModel(String modelName) {
        var tenantRef = UserContextManager.getContext().getTenantRef();
        var result = schemaLoader
            .getModel(tenantRef, UserContextManager.getContext().getDbSchema(), modelName);

        if (result.isEmpty() || StringUtils.equals(modelName, masterSchema)) {
            result = schemaLoader.getModel(tenantRef, masterSchema, modelName);
        }

        return result;
    }
}

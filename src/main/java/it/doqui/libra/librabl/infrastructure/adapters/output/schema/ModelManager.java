package it.doqui.libra.librabl.infrastructure.adapters.output.schema;

import it.doqui.libra.librabl.application.model.configuration.AsyncConfig;
import it.doqui.libra.librabl.application.model.messaging.MessageType;
import it.doqui.libra.librabl.application.ports.out.MessageSenderPort;
import it.doqui.libra.librabl.application.ports.in.TenantUseCase;
import it.doqui.libra.librabl.domain.model.schema.ModelItem;
import it.doqui.libra.librabl.domain.model.schema.*;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TransactionManagerPort;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.NotFoundException;
import it.doqui.libra.librabl.application.model.events.EventType;
import it.doqui.libra.librabl.utils.IOUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.TYPE_CONTENT;

@ApplicationScoped
@Slf4j
public class ModelManager implements ModelManagerPort {

    @Inject
    ModelDelegate modelDelegate;

    @Inject
    SchemaLoader schemaLoader;

    @Inject
    SchemaManager schemaManager;

    @Inject
    TenantUseCase tenantUseCase;

    @Inject
    MessageSenderPort producer;

    @Inject
    AsyncConfig asyncConfig;

    @Inject
    TransactionManagerPort transactionManagerPort;

    @Inject
    SessionContext sessionContext;

    @Override
    public ModelSchema getContextModel() {
        UserContext context = sessionContext.getUserContext();
        if (context == null) {
            throw new RuntimeException("UserContext not available");
        }

        ModelSchema schema = context.getSchema();
        if (schema == null) {
            schema = getModel(context.getTenantRef());
            context.setSchema(schema);
        }

        return schema;
    }

    private ModelSchema getModel(TenantRef tenant) {
        return modelDelegate.findSchema(tenant.toString());
    }

    @Override
    public TypeDescriptor getFlatType(String name) {
        return getContextModel().getFlatType(name, List.of());
    }

    @Override
    public AspectDescriptor getFlatAspect(String name) {
        return getContextModel().getFlatAspect(name);
    }

    @Override
    public Collection<CustomModelSchema> listModels(boolean includeAny) {
        return schemaManager
            .loadSchema(
                sessionContext.getUserContext().getTenantRef(),
                sessionContext.getUserContext().getDbSchema(),
                false,
                includeAny)
            .stream()
            .map(m -> {
                if (m.isActive()) {
                    return getContextModel().getModel(m.getName());
                }

                var model = new CustomModelSchema();
                model.setModelName(m.getName());
                model.setActive(false);
                return model;
            })
            .toList();
    }

    @Override
    public Collection<ModelItem> listStoredModels(boolean includeAny) {
        var tenantRef = sessionContext.getUserContext().getTenantRef();
        return schemaManager
            .loadSchema(tenantRef, sessionContext.getUserContext().getDbSchema(), false, includeAny);
    }

    @Override
    public Optional<ModelItem> getStoredModel(String modelName) {
        return schemaManager.retrieveModel(modelName);
    }

    @Override
    public Optional<CustomModelSchema> getModel(String modelName) {
        return Optional.ofNullable(getContextModel().getModel(modelName));
    }

    @Override
    public Optional<CustomModelSchema> getNamespaceSchema(String name) {
        return Optional.ofNullable(getContextModel().getNamespaceSchema(name));
    }

    @Override
    public void deployModel(String fmt, InputStream is) throws IOException {
        var tenantRef = sessionContext.getUserContext().getTenantRef();
        var m = new ModelItem();
        m.setData(new String(IOUtils.readFully(is), StandardCharsets.UTF_8));
        m.setFormat(fmt);
        m.setTenant(tenantRef.toString());

        schemaManager.validate(tenantRef, m);
        schemaLoader.saveModel(tenantRef, sessionContext.getUserContext().getDbSchema(), m);
        tenantUseCase.performSync(tenantRef, false);

        sendReloadTenant(m.getTenant(), false);
    }

    @Override
    public void reloadTenant(String tenant) {
        sendReloadTenant(tenant, true);
    }

    private void sendReloadTenant(String tenant, boolean includeMySelf) {
        producer.submit(context -> {
                var message = context.createMapMessage();
                message.setJMSType(MessageType.DISTRIBUTED_EVENT);
                message.setStringProperty("event", EventType.RELOAD_TENANT);
                message.setStringProperty("tenant", tenant);
                message.setStringProperty("sender", transactionManagerPort.getInstanceId());
                message.setBooleanProperty("includeMySelf", includeMySelf);

                return message;
            },
            "topic:" + asyncConfig.producer().eventsTopic()
        );
    }

    @Override
    public void undeployModel(String modelName) {
        var tenantRef = sessionContext.getUserContext().getTenantRef();
        var schema = schemaManager.getTenantSchema(tenantRef.toString());
        var model = schema.getNamespaces().values().stream()
            .filter(m -> StringUtils.equals(modelName, m.getModelName()))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(modelName));

        var namespaceSet = model.getNamespaces().stream()
            .map(ModelNamespace::getPrefix)
            .collect(Collectors.toSet());

        schema.getNamespaces().values().stream()
            .filter(m -> !StringUtils.equals(modelName, m.getModelName()))
            .filter(m -> !Collections.disjoint(m.getImports(), namespaceSet))
            .findAny()
            .ifPresent(x -> {
                throw new ForbiddenException(String.format("Cannot undeploy model '%s': some declared namespace are imported by other models", modelName));
            });

        schemaManager.undeploy(schema, sessionContext.getUserContext().getDbSchema(), model);
        sendReloadTenant(tenantRef.toString(), false);
        try {
            tenantUseCase.performSync(tenantRef, false);
        } catch (Exception e) {
            log.warn("Solr synchronization of tenant '{}' failed", tenantRef, e);
        }
    }

    public String findContentProperty(final ModelSchema schema, final TypeDescriptor td, final String contentPropertyName) {
        if (StringUtils.isNotBlank(contentPropertyName)) {
            var pd = schema.getProperty(contentPropertyName);
            if (pd == null) {
                throw new BadRequestException(String.format("Property %s not found", contentPropertyName));
            }

            if (!StringUtils.equals(pd.getType(), TYPE_CONTENT)) {
                throw new BadRequestException(String.format("The property '%s' must be of type %s", contentPropertyName, TYPE_CONTENT));
            }

            if (pd.isDeclarationRequired() && !td.getMandatoryProperties().contains(contentPropertyName) && !td.getSuggestedProperties().contains(contentPropertyName)) {
                throw new BadRequestException(String.format("The content property '%s' must be declared by type %s either as mandatory or as suggested property", contentPropertyName, td.getName()));
            }

            return contentPropertyName;
        } else {
            PropertyDescriptor foundPd = null;
            for (String name : td.getMandatoryProperties()) {
                PropertyDescriptor pd = schema.getProperty(name);
                if (StringUtils.equals(pd.getType(), TYPE_CONTENT)) {
                    foundPd = pd;
                }
            }

            if (foundPd == null) {
                for (String name : td.getSuggestedProperties()) {
                    PropertyDescriptor pd = schema.getProperty(name);
                    if (StringUtils.equals(pd.getType(), TYPE_CONTENT)) {
                        foundPd = pd;
                    }
                }
            }

            if (foundPd == null) {
                throw new BadRequestException(String.format("No property of type %s declared by type %s", TYPE_CONTENT, td.getName()));
            }

            return foundPd.getName();
        }
    }
}

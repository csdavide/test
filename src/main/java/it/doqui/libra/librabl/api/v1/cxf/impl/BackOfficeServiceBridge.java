package it.doqui.libra.librabl.api.v1.cxf.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.index.ecmengine.mtom.dto.*;
import it.doqui.index.ecmengine.mtom.exception.*;
import it.doqui.libra.librabl.api.v1.rest.dto.ReindexParameters;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.ModelService;
import it.doqui.libra.librabl.business.service.interfaces.ReindexService;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.schema.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@ApplicationScoped
@Slf4j
public class BackOfficeServiceBridge extends AbstractServiceBridge {

    @Inject
    ModelService modelService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ReindexService reindexService;

    public void createTenant(Tenant tenant, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        EcmEngineTransactionException, EcmEngineException {
        throw new PermissionDeniedException("Tenant creation requires system admin role");
    }

    public boolean onlineReindex(ReindexParameters params, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, PermissionDeniedException,
        EcmEngineTransactionException, EcmEngineException {
        validate(() -> {
            Objects.requireNonNull(params, "ReindexParameters must not be null");
            if (!StringUtils.equals(params.getCommand(), "PUNCTUAL")) {
                throw new InvalidParameterException("Unsupported command " + params.getCommand());
            }
        });
        return call(context, () -> {
            var tenantRef = TenantRef.valueOf(UserContextManager.getTenant());
            if (params.getIds() != null && params.getIds().length > 0) {
                var transactions = Arrays.stream(params.getIds()).map(Long::parseLong).toList();
                reindexService.syncReindexTransactions(tenantRef, transactions);
            }

            if (params.getUuids() != null && params.getUuids().length > 0) {
                reindexService.syncReindexNodes(tenantRef, Arrays.asList(params.getUuids()));
            }

            return true;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public SystemInfo getSystemInfo(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException {
        return call(context, () -> {
            var runtime = Runtime.getRuntime();
            var systemInfo = new SystemInfo();

            try {
                ClassLoader cl = this.getClass().getClassLoader();
                InputStream is = cl.getResourceAsStream("git.json");
                if (is != null) {
                    var map = objectMapper.readValue(is, Map.class);
                    var version = map.get("git.build.version");
                    var time = map.get("git.build.time");
                    var tags = map.get("git.tags");
                    var description = String.format("%s%s built at %s", version, StringUtils.isBlank(tags.toString()) ? "" : " " + tags, time.toString());
                    systemInfo.setEcmengineVersion(description);
                }
            } catch (Exception e) {
                // ignore
            }

            systemInfo.setHeapSize(runtime.totalMemory());
            systemInfo.setHeapMaxSize(runtime.maxMemory());
            systemInfo.setHeapFreeSize(runtime.freeMemory());
            return systemInfo;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public CustomModel[] getAllCustomModels(MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException {
        return call(context, () -> modelService
            .listStoredModels(false)
            .stream()
            .map(m -> {
                byte[] buffer = m.getData().getBytes(StandardCharsets.UTF_8);
                CustomModel c = new CustomModel();
                c.setPrefixedName(m.getName());
                c.setFilename(m.getName().replace(':', '_') + "." + m.getFormat());
                c.setData(buffer);
                c.setActive(m.isActive());

                modelService.getModel(m.getName()).ifPresent(model -> {
                    c.setDescription(model.getDescription());
                });

                return c;
            })
            .toList()
            .toArray(new CustomModel[0]));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.CREATE)
    public void deployCustomModel(CustomModel model, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException {
        validate(() -> {
            Objects.requireNonNull(model, "Missing model");
            Objects.requireNonNull(model.getData(), "Missing model data");
        });

        call(context, () -> {
            modelService.deployModel("xml", new ByteArrayInputStream(model.getData()));
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.DELETE)
    public void undeployCustomModel(CustomModel model, MtomOperationContext context) throws InvalidParameterException,
        InvalidCredentialsException, PermissionDeniedException, EcmEngineException {
        validate(() -> {
            Objects.requireNonNull(model, "Missing model");

            if (model.getPrefixedName() == null) {
                Objects.requireNonNull(model.getFilename(), "Missing both model and file name");

                var dot = model.getFilename().lastIndexOf('.');
                var name = dot < 0 ? model.getFilename() : model.getFilename().substring(0, dot);
                name = name.replace("_", ":");
                model.setPrefixedName(name);
            }

            if (StringUtils.countMatches(model.getPrefixedName(), ':') != 1 || !PrefixedQName.valueOf(model.getPrefixedName()).hasNamespace()) {
                throw new BadRequestException("Invalid model name: " + model.getPrefixedName());
            }
        });

        call(context, () -> {
            modelService.undeployModel(model.getPrefixedName());
            return null;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public ModelDescriptor[] getAllModelDescriptors(MtomOperationContext context) {
        return call(context, () -> modelService
            .listModels(false)
            .stream()
            .map(m -> {
                ModelDescriptor c = new ModelDescriptor();
                c.setPrefixedName(m.getModelName());
                c.setDescription(m.getDescription());
                return c;
            })
            .toList()
            .toArray(new ModelDescriptor[0]));
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public ModelMetadata getModelDefinition(ModelDescriptor modelDescriptor, MtomOperationContext context)
        throws InvalidParameterException, InvalidCredentialsException, NoDataExtractedException,
        EcmEngineException, EcmEngineTransactionException {
        validate(() -> {
            requireNonNull(modelDescriptor, "ModelDescriptor");
            requireNonNull(modelDescriptor.getPrefixedName(), "ModelDescriptor prefixedName");
        });

        return call(context, () -> {
            var model = modelService
                .getModel(modelDescriptor.getPrefixedName())
                .orElseThrow(() -> new NoDataExtractedException(modelDescriptor.getPrefixedName()));

            var mm = new ModelMetadata();
            mm.setPrefixedName(model.getModelName());
            mm.setDescription(model.getDescription());

            mm.setAspects(
                model.getAspects()
                    .stream()
                    .map(a -> mapAspect(a, model))
                    .toList()
                    .toArray(new AspectMetadata[0])
            );

            mm.setTypes(
                model.getTypes()
                    .stream()
                    .map(t -> mapType(t, model))
                    .toList()
                    .toArray(new TypeMetadata[0])
            );

            return mm;
        });
    }

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public TypeMetadata getTypeDefinition(ModelDescriptor modelDescriptor, MtomOperationContext context) {
        validate(() -> {
            requireNonNull(modelDescriptor, "ModelDescriptor");
            requireNonNull(modelDescriptor.getPrefixedName(), "ModelDescriptor prefixedName");
        });

        return call(context, () -> {
            var model = modelService
                .getNamespaceSchema(modelDescriptor.getPrefixedName())
                .orElseThrow(() -> new NoDataExtractedException(modelDescriptor.getPrefixedName()));

            return Optional.ofNullable(model.getType(modelDescriptor.getPrefixedName()))
                .map(t -> mapType(t, model))
                .orElseThrow(() -> new NoDataExtractedException(modelDescriptor.getPrefixedName()));
        });
    }

    private AspectMetadata mapAspect(AspectDescriptor a, CustomModelSchema model) {
        var am = new AspectMetadata();
        am.setPrefixedName(a.getName());
        am.setTitle(a.getTitle());
        am.setParentPrefixedName(a.getParent());
        am.setProperties(properties(a, model));

        am.setAssociations(
            model.getParentTypeAssociations(a.getName())
                .stream()
                .filter(AssociationDescriptor::isLight)
                .map(this::mapAssociation)
                .toList()
                .toArray(new AssociationMetadata[0])
        );

        am.setChildAssociations(
            model.getParentTypeAssociations(a.getName())
                .stream()
                .filter(x -> !x.isLight())
                .map(this::mapChildAssociation)
                .toList()
                .toArray(new ChildAssociationMetadata[0])
        );

        return am;
    }

    private TypeMetadata mapType(TypeDescriptor t, CustomModelSchema model) {
        var tm = new TypeMetadata();
        tm.setPrefixedName(t.getName());
        tm.setTitle(t.getTitle());
        tm.setParentPrefixedName(t.getParent());
        tm.setProperties(properties(t, model));

        tm.setAssociations(
            model.getParentTypeAssociations(t.getName())
                .stream()
                .filter(AssociationDescriptor::isLight)
                .map(this::mapAssociation)
                .toList()
                .toArray(new AssociationMetadata[0])
        );

        tm.setChildAssociations(
            model.getParentTypeAssociations(t.getName())
                .stream()
                .filter(x -> !x.isLight())
                .map(this::mapChildAssociation)
                .toList()
                .toArray(new ChildAssociationMetadata[0])
        );

        return tm;
    }

    private PropertyMetadata[] properties(TypedInterfaceDescriptor t, CustomModelSchema model) {
        var md = new ModelDescriptor();
        md.setPrefixedName(model.getModelName());

        var result = new ArrayList<PropertyMetadata>();
        t.getMandatoryProperties()
            .stream()
            .map(model::getProperty)
            .map(p -> mapProperty(p, true))
            .peek(p -> p.setModelDescriptor(md))
            .forEach(result::add);

        t.getSuggestedProperties()
            .stream()
            .map(model::getProperty)
            .map(p -> mapProperty(p, false))
            .peek(p -> p.setModelDescriptor(md))
            .forEach(result::add);

        return result.toArray(new PropertyMetadata[0]);
    }

    private PropertyMetadata mapProperty(PropertyDescriptor p, boolean mandatory) {
        var pm = new PropertyMetadata();
        pm.setPrefixedName(p.getName());
        pm.setTitle(p.getTitle());
        pm.setDataType(p.getType());
        pm.setMultiValued(pm.isMultiValued());
        pm.setModifiable(pm.isModifiable());
        pm.setMandatory(mandatory);

        return pm;
    }

    private AssociationMetadata mapAssociation(AssociationDescriptor ad) {
        var am = new AssociationMetadata();
        am.setPrefixedName(ad.getName());
        return am;
    }

    private ChildAssociationMetadata mapChildAssociation(AssociationDescriptor ad) {
        var am = new ChildAssociationMetadata();
        am.setPrefixedName(ad.getName());
        return am;
    }
}

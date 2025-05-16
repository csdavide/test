package it.doqui.libra.librabl.business.provider.schema.impl;

import it.doqui.libra.librabl.business.provider.schema.ConstraintRegister;
import it.doqui.libra.librabl.views.schema.CustomModelSchema;
import it.doqui.libra.librabl.views.schema.ModelNamespace;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.views.schema.TypedInterfaceDescriptor;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadRequestException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.stream.Collectors;

import static it.doqui.libra.librabl.business.provider.mappers.PropertyConverter.*;

@ApplicationScoped
@Slf4j
public class SchemaValidator {

    @Inject
    ConstraintRegister constraintRegister;

    boolean validate(TenantSchema schema,  TenantSchema commonSchema, CustomModelSchema model) {
        log.debug("Validating model {}", model.getModelName());

        try {
            if (StringUtils.isBlank(model.getModelName())) {
                throw new PreconditionFailedException("Anonymous model");
            }

            var importMap = performImports(schema, commonSchema, model);
            validateProperties(model, importMap);
            validateTypedInterfaces(model, importMap);

            return true;
        } catch (BadRequestException | PreconditionFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error(String.format("Model %s validation failed: %s", model.getModelName(), e.getMessage()), e);
            throw e;
        }
    }

    private Map<String,CustomModelSchema> performImports(TenantSchema schema, TenantSchema commonSchema, CustomModelSchema model) {
        var chain = new SchemaChain();
        chain.getSchemas().add(schema);
        if (commonSchema != null) {
            chain.getSchemas().add(commonSchema);
        }

//        try {
//            log.debug("T {}, NS {} -> {}", schema.getTenant(), schema.getNamespaces().keySet(), commonSchema.getNamespaces().keySet());
//        } catch (Exception e) {
//            log.error(String.format("Chain contains namespaces failed: %s", e.getMessage()), e);
//        }

        var importMap = new HashMap<String,CustomModelSchema>();
        for (var ns : model.getImports()) {
            importNS(ns, chain, importMap);
        }

        for (var n : model.getNamespaces()) {
            importMap.put(n.getPrefix(), model);
        }

        return importMap;
    }

    private void importNS(String ns, ModelSchema schema, Map<String, CustomModelSchema> importMap) {
        log.debug("Importing namespace {} from {}", ns, schema.listNamespaceNames());
        var m = schema.getNamespaceSchema(ns);
        if (m == null) {
            throw new PreconditionFailedException("Cannot import namespace " + ns);
        }
        importMap.put(ns, m);
    }

    private void validateProperties(CustomModelSchema model, Map<String,CustomModelSchema> importMap) {
        var processedSet = new HashSet<String>();
        var nsSet = model.getNamespaces().stream().map(ModelNamespace::getPrefix).collect(Collectors.toSet());
        for (var pd : model.getProperties()) {
            log.debug("Validating property {}", pd.getName());
            if (processedSet.contains(pd.getName())) {
                throw new PreconditionFailedException("Property " + pd.getName() + " not unique");
            }

            processedSet.add(pd.getName());
            var pname = PrefixedQName.valueOf(pd.getName());
            if (!nsSet.contains(pname.getNamespaceURI())) {
                throw new PreconditionFailedException("Invalid namespace in property " + pd.getName());
            }

            validatePropertyType(pd.getName(), pd.getType(), importMap);
            for (var cname : pd.getConstraints()) {
                var _cname = PrefixedQName.valueOf(cname);
                if (!importMap.containsKey(_cname.getNamespaceURI())) {
                    throw new PreconditionFailedException(String.format("Invalid namespace in constraint %s referenced by property %s", cname, pname));
                }

                var cd = importMap.values().stream()
                    .map(m -> m.getConstraint(cname))
                    .filter(Objects::nonNull).findFirst()
                    .orElseThrow(() -> new PreconditionFailedException(String.format("Missing constraint %s referenced by property %s", cname, pname)));

                if (constraintRegister.getValidator(cd.getType()) == null) {
                    throw new PreconditionFailedException("Unknown constraint type " + cd.getType());
                }
            }
        }

        var dynamicSet = new HashSet<String>();
        for (var pd : model.getDynamicProperties()) {
            log.debug("Validating dynamic property {}", pd.getName());
            if (processedSet.contains(pd.getName())) {
                throw new PreconditionFailedException("Dynamic property " + pd.getName() + " not unique");
            }

            processedSet.add(pd.getName());
            var pname = PrefixedQName.valueOf(pd.getName());
            if (!nsSet.contains(pname.getNamespaceURI())) {
                throw new PreconditionFailedException("Invalid namespace in dynamic property " + pd.getName());
            }

            if (!pd.isPredefined() && !StringUtils.endsWith(pname.getLocalPart(), "*") || StringUtils.countMatches(pname.getLocalPart(), '*') != 1) {
                throw new PreconditionFailedException("Dynamic property " + pd.getName() + " has a wrong name: expected format 'ns:prefix*'");
            }

            validatePropertyType(pd.getName(), pd.getType(), importMap);

            // check for duplicated dynamic properties
            var star = pd.getName().indexOf('*');
            var prefix = star < 0 ? pd.getName() : pd.getName().substring(0, star);
            dynamicSet.stream().filter(prefix::startsWith).findFirst().ifPresent(p -> {
                throw new PreconditionFailedException(String.format("Invalid dynamic property '%s': found another prefixed property '%s*'", pd.getName(), p));
            });

            dynamicSet.add(prefix);
        }
    }

    private void validatePropertyType(String name, String type, Map<String,CustomModelSchema> importMap) {
        var ptype = PrefixedQName.valueOf(type);
        if (!importMap.containsKey(ptype.getNamespaceURI())) {
            throw new PreconditionFailedException(String.format("Invalid namespace in type %s of property %s", type, name));
        }

        switch (type) {
            case "d:any":
            case "d:text":
            case "d:int":
            case "d:long":
            case "d:boolean":
            case "d:float":
            case "d:double":
            case TYPE_DATE:
            case TYPE_DATETIME:
            case TYPE_MLTEXT:
            case TYPE_CONTENT:
            case "d:qname":
            case "d:locale":
            case "d:category":
            case "d:noderef":
                break;

            case "d:childassocref":
            case "d:assocref":
                log.warn("Unsupported data type " + type + " in property " + name);
                break;

            default:
                throw new PreconditionFailedException("Unknown data type " + type + " in property " + name);
        }
    }

    private void validateTypedInterfaces(CustomModelSchema model, Map<String,CustomModelSchema> importMap) {
        var processedSet = new HashSet<String>();
        var nsSet = model.getNamespaces().stream().map(ModelNamespace::getPrefix).collect(Collectors.toSet());

        for (var t : model.getTypes()) {
            validateTypedInterface("type", processedSet, t, model, nsSet, importMap);
        }

        for (var t : model.getAspects()) {
            validateTypedInterface("aspect", processedSet, t, model, nsSet, importMap);
        }
    }

    private void validateTypedInterface(String kind, Set<String> processedSet, TypedInterfaceDescriptor t, CustomModelSchema model, Set<String> nsSet, Map<String,CustomModelSchema> importMap) {
        log.debug("Validating {} {}", kind, t.getName());
        if (processedSet.contains(t.getName())) {
            throw new PreconditionFailedException(StringUtils.capitalize(kind) + " " + t.getName() + " not unique");
        }

        processedSet.add(t.getName());
        var pname = PrefixedQName.valueOf(t.getName());
        if (!nsSet.contains(pname.getNamespaceURI())) {
            throw new PreconditionFailedException("Invalid namespace in " + kind + " " + t.getName());
        }

        if (StringUtils.isBlank(t.getParent())) {
            if (StringUtils.equals(kind, "type") && !StringUtils.equals(t.getName(), "sys:base")) {
                throw new PreconditionFailedException("No parent for type " + t.getName());
            }
        } else {
            checkIfDefined(kind, t.getParent(), importMap);
        }

        t.getMandatoryProperties().forEach(name -> checkIfDefined("property", name, importMap));
        t.getSuggestedProperties().forEach(name -> checkIfDefined("property", name, importMap));
        t.getMandatoryAspects().forEach(name -> checkIfDefined("aspect", name, importMap));
    }

    private void checkIfDefined(String kind, String name, Map<String,CustomModelSchema> importMap) {
        var model = importMap.get(PrefixedQName.valueOf(name).getNamespaceURI());
        if (model == null) {
            throw new PreconditionFailedException("Invalid namespace in " + kind + " " + name);
        }

        final Object q = switch (kind) {
            case "type" -> model.getType(name);
            case "aspect" -> model.getAspect(name);
            case "property" -> model.getProperty(name);
            default -> throw new RuntimeException("Unexpected kind " + kind);
        };

        if (q == null) {
            throw new PreconditionFailedException("Cannot find " + kind + " " + name);
        }
    }
}

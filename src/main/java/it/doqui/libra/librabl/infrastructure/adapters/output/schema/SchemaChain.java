package it.doqui.libra.librabl.infrastructure.adapters.output.schema;

import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.domain.model.schema.*;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@ToString
@Slf4j
public class SchemaChain implements ModelSchema {
    private final LinkedList<TenantSchema> schemas;

    public SchemaChain() {
        this.schemas = new LinkedList<>();
    }

    @Override
    public CustomModelSchema getModel(final String modelName) {
        CustomModelSchema model = null;
        for (TenantSchema t : schemas) {
            if (t != null) {
                model = t.getNamespaces().values().stream()
                    .filter(c -> Strings.CI.equals(c.getModelName(), modelName))
                    .findFirst()
                    .orElse(null);
                if (model != null) {
                    break;
                }
            }
        }
        return model;
    }

    @Override
    public List<String> listNamespaceNames() {
        List<String> namespaces = new LinkedList<>();
        for (TenantSchema t : schemas) {
            if (t != null) {
                namespaces.addAll(t.getNamespaces().keySet());
            }
        }
        return namespaces;
    }

    @Override
    public CustomModelSchema getNamespaceSchema(final String name) {
        CustomModelSchema model = null;
        String ns = Strings.CS.contains(name, ":")
            ? PrefixedQName.valueOf(name).getNamespaceURI()
            : name;
        for (TenantSchema t : schemas) {
            if (t != null) {
                model = t.getNamespaces().get(ns);
                if (model != null) {
                    break;
                }
            }
        }

        return model;
    }

    @Override
    public String getNamespace(final URI uri) {
        String ns = null;
        for (TenantSchema t : schemas) {
            ns = t.getNamespaceMap().inverse().get(uri);
            if (ns != null) {
                break;
            }
        }

        return ns;
    }

    @Override
    public PropertyDescriptor getProperty(String name) {
        CustomModelSchema model = getNamespaceSchema(name);
        return model != null ? model.getProperty(name) : null;
    }

    @Override
    public TypeDescriptor getType(String name) {
        CustomModelSchema model = getNamespaceSchema(name);
        return model != null ? model.getType(name) : null;
    }

    @Override
    public AspectDescriptor getAspect(String name) {
        CustomModelSchema model = getNamespaceSchema(name);
        return model != null ? model.getAspect(name) : null;
    }

    @Override
    public ConstraintDescriptor getConstraint(String name) {
        CustomModelSchema model = getNamespaceSchema(name);
        return model != null ? model.getConstraint(name) : null;
    }

    @Override
    public AssociationDescriptor getAssociation(String name) {
        CustomModelSchema model = getNamespaceSchema(name);
        return model != null ? model.getAssociation(name) : null;
    }

    @Override
    public Collection<AssociationDescriptor> getParentTypeAssociations(String parentType) {
        CustomModelSchema model = getNamespaceSchema(parentType);
        return model != null ? model.getParentTypeAssociations(parentType) : null;
    }

    @Override
    public List<TypeDescriptor> getTypeHierarchy(String name) {
        TypeDescriptor type = getType(name);
        if (type == null) {
            throw new BadDataException("Undefined type " + name);
        }

        List<TypeDescriptor> hierarchy = new LinkedList<>();
        hierarchy.add(type);

        if (type.getParent() != null) {
            hierarchy.addAll(getTypeHierarchy(type.getParent()));
        }

        return hierarchy;
    }

    @Override
    public List<AspectDescriptor> getAspectHierarchy(String name) {
        AspectDescriptor aspect = getAspect(name);
        if (aspect == null) {
            if (Strings.CS.startsWith(name, "ecm-sys:")) {
                aspect = new AspectDescriptor();
                aspect.setName(name);
            } else {
                throw new RuntimeException("Undefined aspect " + name);
            }
        }

        List<AspectDescriptor> hierarchy = new LinkedList<>();
        hierarchy.add(aspect);
        if (aspect.getParent() != null) {
            hierarchy.addAll(getAspectHierarchy(aspect.getParent()));
        }

        return hierarchy;
    }

    @Override
    public AspectDescriptor getFlatAspect(String name) {
        List<AspectDescriptor> hierarchy = getAspectHierarchy(name);
        if (hierarchy.isEmpty()) {
            return null;
        }

        AspectDescriptor aspect = hierarchy.get(0).copyTo(AspectDescriptor.class);
        for (int i = 1; i < hierarchy.size(); i++) {
            AspectDescriptor h = hierarchy.get(i);
            aspect.getMandatoryProperties().addAll(h.getMandatoryProperties());

            for (String a : h.getMandatoryAspects()) {
                if (!aspect.getMandatoryAspects().contains(a)) {
                    AspectDescriptor flatA = getFlatAspect(a);
                    aspect.getMandatoryProperties().addAll(flatA.getMandatoryProperties());
                    aspect.getSuggestedProperties().addAll(flatA.getSuggestedProperties());
                    aspect.getMandatoryAspects().addAll(flatA.getMandatoryAspects());
                    aspect.getMandatoryAspects().add(a);
                    aspect.getAncestors().addAll(flatA.getAncestors());
                }
            }

            aspect.getAncestors().addAll(h.getAncestors());
        }

        return aspect;
    }

    @Override
    public TypeDescriptor getFlatType(String name, Collection<String> aspects) {
        List<TypeDescriptor> hierarchy = getTypeHierarchy(name);
        if (hierarchy.isEmpty()) {
            return null;
        }

        TypeDescriptor type = hierarchy.get(0).copyTo(TypeDescriptor.class);
        var mandatoryProperties = new ArrayList<>(type.getMandatoryProperties());
        var suggestedProperties = new ArrayList<>(type.getSuggestedProperties());
        var mandatoryAspects = new ArrayList<>(type.getMandatoryAspects());

        fillTypeAttrsWithAspectProperties(type.getMandatoryAspects(), mandatoryProperties, suggestedProperties, mandatoryAspects);
        fillTypeAttrsWithAspectProperties(aspects, mandatoryProperties, suggestedProperties, mandatoryAspects);
        for (int i = 1; i < hierarchy.size(); i++) {
            TypeDescriptor h = hierarchy.get(i);
            mandatoryProperties.addAll(h.getMandatoryProperties());
            suggestedProperties.addAll(h.getSuggestedProperties());
            type.getAncestors().add(h.getName());

            fillTypeAttrsWithAspectProperties(h.getMandatoryAspects(), mandatoryProperties, suggestedProperties, mandatoryAspects);
        }

        type.getMandatoryProperties().addAll(mandatoryProperties.stream().collect(Collectors.toUnmodifiableSet()));
        type.getSuggestedProperties().addAll(suggestedProperties.stream().collect(Collectors.toUnmodifiableSet()));
        type.getMandatoryAspects().addAll(mandatoryAspects.stream().collect(Collectors.toUnmodifiableSet()));

        return type;
    }

    private void fillTypeAttrsWithAspectProperties(Collection<String> aspects, List<String> mandatoryProperties, List<String> suggestedProperties, List<String> mandatoryAspects) {
        for (String a : aspects) {
            AspectDescriptor flatA = getFlatAspect(a);
            mandatoryProperties.addAll(flatA.getMandatoryProperties());
            suggestedProperties.addAll(flatA.getSuggestedProperties());
            mandatoryAspects.addAll(flatA.getMandatoryAspects());
            mandatoryAspects.add(a);
        }
    }
}

package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.mappers.PropertyConverter;
import it.doqui.libra.librabl.domain.ports.out.ConstraintRegister;
import it.doqui.libra.librabl.domain.policy.PropertyConstraintValidator;
import it.doqui.libra.librabl.domain.model.schema.PropertyContainer;
import it.doqui.libra.librabl.domain.model.graph.NodeDescriptor;
import it.doqui.libra.librabl.domain.model.schema.AssociationDescriptor;
import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.domain.model.schema.PropertyDescriptor;
import it.doqui.libra.librabl.domain.ports.out.ModelManagerPort;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.ActiveNode;
import it.doqui.libra.librabl.infrastructure.adapters.output.schema.AlwaysOkConstraintValidator;
import it.doqui.libra.librabl.infrastructure.adapters.output.schema.EnumConstraintValidator;
import it.doqui.libra.librabl.infrastructure.adapters.output.schema.MinMaxConstraintValidator;
import it.doqui.libra.librabl.infrastructure.adapters.output.schema.RegexConstraintValidator;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static it.doqui.libra.librabl.domain.model.graph.Constants.*;
import static it.doqui.libra.librabl.domain.model.schema.DictionaryTypes.TYPE_CONTENT;

@ApplicationScoped
@Slf4j
public class NodeValidator implements ConstraintRegister {

    @Inject
    ModelManagerPort modelManager;

    @Inject
    PropertyConverter propertyConverter;

    private final Map<String, PropertyConstraintValidator> validators;
    private final Set<String> managedProperties = Set.of(
        "sys:node-dbid", "sys:node-uuid", "sys:store-protocol", "sys:store-identifier", "ecm-sys:version",
        PROP_ECMSYS_PUBLIC_LINK, CM_CREATOR, CM_CREATED, CM_MODIFIER, CM_MODIFIED, CM_SOURCE
    );

    public NodeValidator() {
        this.validators = new ConcurrentHashMap<>();
    }

    @PostConstruct
    void init() {
        register("REGEX", new RegexConstraintValidator());
        register("ENUM", new EnumConstraintValidator());
        register("LIST", new EnumConstraintValidator());
        register("MINMAX", new MinMaxConstraintValidator());
        register("OK", new AlwaysOkConstraintValidator());
        //register("CLASS", new CustomConstraintValidator());
    }

    @Override
    public void register(String type, PropertyConstraintValidator validator) {
        validators.put(type, validator);
        log.info("Registered constraint for type '{}': {}", type, validator.getClass().getName());
    }

    @Override
    public PropertyConstraintValidator getValidator(String type) {
        return validators.get(type);
    }

    public void validateMetadata(ModelSchema schema, ActiveNode node) {
        var type = schema.getFlatType(node.getTypeName(), node.getAspects());
        if (type.getNamePadding() != null) {
            var name = ObjectUtils.getAsString(node.getProperties().get(CM_NAME));
            if (name != null) {
                var padding = type.getNamePadding().length() - name.length();
                if (padding > 0) {
                    name = type.getNamePadding().substring(0, padding) + name;
                    node.getProperties().put(CM_NAME, name);
                }
            }
        }

        node.getAspects().addAll(type.getMandatoryAspects());

        var checkedSet = new HashSet<>();
        for (var name : type.getMandatoryProperties()) {
            if (!managedProperties.contains(name) && node.getData().getProperties().get(name) == null) {
                var pd = schema.getProperty(name);
                if (Strings.CS.equals(pd.getType(), TYPE_CONTENT)) {
                    if (node.getData().getProperties().containsKey(name)) {
                        throw new BadDataException(String.format("Mandatory content property '%s' cannot be removed", name));
                    } else if (node.getData().getContents().stream().filter(c -> Strings.CS.equals(name, c.getName())).findAny().isEmpty()) {
                        throw new BadDataException(String.format("Missing mandatory content property '%s'", name));
                    }
                } else {
                    if (pd.getDefaultValue() == null) {
                        throw new BadDataException(String.format("Missing mandatory property '%s'", name));
                    }

                    var value = propertyConverter.convertPropertyValue(pd, pd.getDefaultValue());
                    node.getData().getProperties().put(name, value);
                }
            }
            checkedSet.add(name);
        }

        for (var name : node.getData().getProperties().keySet()) {
            if (!checkedSet.contains(name)) {
                var pd = schema.getProperty(name);
                if (pd == null) {
                    log.warn("Property '{}' no more available", name);
                } else if (pd.isDeclarationRequired() && !type.getSuggestedProperties().contains(name)) {
                    throw new BadDataException(String.format("Property '%s' requires use declaration", name));
                }
            }
        }
    }

    public boolean validateConstraints(PropertyContainer pc) {
        validateConstraints(pc.getDescriptor(), pc.getValue());
        return true;
    }

    private void validateConstraints(PropertyDescriptor pd, Object value) {
        ModelSchema schema = modelManager.getContextModel();
        for (String cname : pd.getConstraints()) {
            var cd = schema.getConstraint(cname);
            PropertyConstraintValidator validator = validators.get(cd.getType());
            if (validator == null) {
                throw new RuntimeException("Unable to find a validator for type " + cd.getType());
            }

            validator.validate(cd, value);
        }
    }

    public void validateAssociation(NodeDescriptor parent, NodeDescriptor child, String association) {
        ModelSchema schema = modelManager.getContextModel();
        AssociationDescriptor ad = schema.getAssociation(association);
        if (ad == null) {
            throw new ConstraintException("Association not defined: " + association);
        }

        if (ad.getParent() != null && isTypeNotExtending(schema, parent, ad.getParent())) {
            throw new ConstraintException(String.format("Parent does not match association %s: %s required", association, ad.getParent()));
        }

        if (ad.getChild() != null && isTypeNotExtending(schema, child, ad.getChild())) {
            throw new ConstraintException(String.format("Child does not match association %s: %s required", association, ad.getChild()));
        }
    }

    public void validateAssociationName(String name) {
        if (name == null) {
            throw new ConstraintException("Missing association name");
        }

        PrefixedQName prefixedName = PrefixedQName.valueOf(name);
        if (prefixedName.hasNamespace()) {
            ModelSchema schema = modelManager.getContextModel();
            if (schema.getNamespaceSchema(prefixedName.getNamespaceURI()) == null) {
                throw new ConstraintException(String.format("Invalid association name '%s': unknown namespace", name));
            }
        }

        if (StringUtils.containsAny(prefixedName.getLocalPart(), '/', '\\', '*', ':')) {
            throw new ConstraintException(String.format("Invalid association name '%s': illegal character", name));
        }
    }

    private boolean isTypeNotExtending(ModelSchema schema, NodeDescriptor node, String requiredType) {
        var typeHierarchy = schema.getTypeHierarchy(node.getTypeName());
        for (var type : typeHierarchy) {
            if (type.getName().equals(requiredType)) {
                return false;
            }
        }

        for (var aspectName : node.getAspects()) {
            var aspectHierarchy = schema.getAspectHierarchy(aspectName);
            for (var aspect : aspectHierarchy) {
                if (aspect.getName().equals(requiredType)) {
                    return false;
                }
            }
        }

        return true;
    }
}

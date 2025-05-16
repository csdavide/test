package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.mappers.PropertyConverter;
import it.doqui.libra.librabl.business.provider.schema.ConstraintRegister;
import it.doqui.libra.librabl.business.provider.schema.ModelManager;
import it.doqui.libra.librabl.business.provider.schema.PropertyConstraintValidator;
import it.doqui.libra.librabl.business.provider.schema.impl.AlwaysOkConstraintValidator;
import it.doqui.libra.librabl.business.provider.schema.impl.EnumConstraintValidator;
import it.doqui.libra.librabl.business.provider.schema.impl.MinMaxConstraintValidator;
import it.doqui.libra.librabl.business.provider.schema.impl.RegexConstraintValidator;
import it.doqui.libra.librabl.business.service.node.PropertyContainer;
import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.NodeDescriptor;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.exceptions.ConstraintException;
import it.doqui.libra.librabl.views.schema.AssociationDescriptor;
import it.doqui.libra.librabl.views.schema.PropertyDescriptor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static it.doqui.libra.librabl.business.provider.mappers.PropertyConverter.TYPE_CONTENT;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.*;

@ApplicationScoped
@Slf4j
public class NodeValidator implements ConstraintRegister {

    @Inject
    ModelManager modelManager;

    @Inject
    PropertyConverter propertyConverter;

    private final Map<String, PropertyConstraintValidator> validators;
    private final Set<String> managedProperties = Set.of(
        "sys:node-dbid", "sys:node-uuid", "sys:store-protocol", "sys:store-identifier", "ecm-sys:version",
        CM_CREATOR, CM_CREATED, CM_MODIFIER, CM_MODIFIED
    );

    public NodeValidator() {
        this.validators = new ConcurrentHashMap<>();
    }

    @PostConstruct
    void init() {
        register("REGEX", new RegexConstraintValidator());
        register("ENUM", new EnumConstraintValidator());
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
        node.getAspects().addAll(type.getMandatoryAspects());

        var checkedSet = new HashSet<>();
        for (var name : type.getMandatoryProperties()) {
            if (!managedProperties.contains(name) && node.getData().getProperties().get(name) == null) {
                var pd = schema.getProperty(name);
                if (StringUtils.equals(pd.getType(), TYPE_CONTENT)) {
                    if (node.getData().getProperties().containsKey(name)) {
                        throw new BadDataException(String.format("Mandatory content property '%s' cannot be removed", name));
                    } else if (node.getData().getContents().stream().filter(c -> StringUtils.equals(name, c.getName())).findAny().isEmpty()) {
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

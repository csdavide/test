package it.doqui.libra.librabl.views.schema;

import com.fasterxml.jackson.annotation.*;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({ "modelName", "version", "description", "author", "published", "defaultTokenizationType", "namespaces", "imports", "properties", "dynamicProperties", "types", "aspects", "associations"})
public class CustomModelSchema {
    @JsonProperty("model")
    private String modelName;

    @JsonIgnore
    private boolean active = true;

    private String description;
    private String author;
    private LocalDate published;
    private String version;
    private String defaultTokenizationType;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<ModelNamespace> namespaces;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final Set<String> imports;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<PropertyDescriptor> properties;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonProperty("dynamic-properties")
    private final List<DynamicPropertyDescriptor> dynamicProperties;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<TypeDescriptor> types;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<AspectDescriptor> aspects;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<AssociationDescriptor> associations;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final List<ConstraintDescriptor> constraints;

    @JsonIgnore
    @Getter(value = AccessLevel.NONE)
    private final Map<String,PropertyDescriptor> propertyMap;

    @JsonIgnore
    @Getter(value = AccessLevel.NONE)
    private final Map<String,TypeDescriptor> typeMap;

    @JsonIgnore
    @Getter(value = AccessLevel.NONE)
    private final Map<String,AspectDescriptor> aspectMap;

    @JsonIgnore
    @Getter(value = AccessLevel.NONE)
    private final Map<String,AssociationDescriptor> associationMap;

    @JsonIgnore
    @Getter(value = AccessLevel.NONE)
    private final Multimap<String,AssociationDescriptor> typeAssociationMap;

    @JsonIgnore
    @Getter(value = AccessLevel.NONE)
    private final Map<String,ConstraintDescriptor> constraintMap;

    public CustomModelSchema() {
        this.namespaces = new LinkedList<>();
        this.imports = new HashSet<>();
        this.properties = new LinkedList<>();
        this.dynamicProperties = new LinkedList<>();
        this.types = new LinkedList<>();
        this.aspects = new LinkedList<>();
        this.associations = new LinkedList<>();
        this.constraints = new LinkedList<>();

        // strutture costruite post-parsing
        this.propertyMap = new ConcurrentHashMap<>();
        this.typeMap = new LinkedHashMap<>();
        this.aspectMap = new LinkedHashMap<>();
        this.associationMap = new LinkedHashMap<>();
        this.typeAssociationMap = LinkedListMultimap.create();
        this.constraintMap = new LinkedHashMap<>();
    }

    public void remap() {
        properties.forEach(p -> propertyMap.put(p.getName(), p));
        types.forEach(t -> typeMap.put(t.getName(), t));
        aspects.forEach(a -> aspectMap.put(a.getName(), a));
        constraints.forEach(c -> constraintMap.put(c.getName(), c));
        associations.forEach(a -> {
            associationMap.put(a.getName(), a);
            typeAssociationMap.put(a.getParent(), a);
        });
    }

    public PropertyDescriptor getProperty(String name) {
        PropertyDescriptor pd = propertyMap.get(name);
        if (pd == null) {
            for (DynamicPropertyDescriptor dd : dynamicProperties) {
                if (StringUtils.isNotBlank(dd.getName())) {
                    String regex = dd.getName();
                    regex = regex.replace("\\","\\\\");
                    regex = regex.replace(".", "\\.");
                    regex = regex.replace("*","\\*");
                    regex = regex.replace("?","\\?");
                    regex = regex.replace(".","\\.");
                    regex = regex.replace("\\*",".*");
                    regex = "^" + regex + "$";

                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(name);
                    if (matcher.matches()) {
                        pd = new PropertyDescriptor();
                        pd.setName(name);
                        pd.setTitle(dd.getTitle());
                        pd.setType(dd.getType());
                        pd.setMultiple(dd.isMultiple());
                        pd.setIndexed(dd.isIndexed());
                        propertyMap.put(name, pd);
                        break;
                    }
                }
            }
        }

        return pd;
    }

    public ConstraintDescriptor getConstraint(String name) {
        return constraintMap.get(name);
    }

    public TypeDescriptor getType(String name) {
        return typeMap.get(name);
    }

    public AspectDescriptor getAspect(String name) {
        return aspectMap.get(name);
    }

    public AssociationDescriptor getAssociation(String name) {
        return associationMap.get(name);
    }

    public Collection<AssociationDescriptor> getParentTypeAssociations(String parentType) {
        return typeAssociationMap.get(parentType);
    }

    public void addConstraint(ConstraintDescriptor constraint) {
        constraints.add(constraint);
        constraintMap.put(constraint.getName(), constraint);
    }

    public void addProperty(PropertyDescriptor descriptor) {
        properties.add(descriptor);
        propertyMap.put(descriptor.getName(), descriptor);
    }

    public void addType(TypeDescriptor descriptor) {
        types.add(descriptor);
        typeMap.put(descriptor.getName(), descriptor);
    }

    public void addAspect(AspectDescriptor descriptor) {
        aspects.add(descriptor);
        aspectMap.put(descriptor.getName(), descriptor);
    }

    public void addAssociation(AssociationDescriptor descriptor) {
        associations.add(descriptor);
        associationMap.put(descriptor.getName(), descriptor);
        typeAssociationMap.put(descriptor.getParent(), descriptor);
    }

    public Optional<URI> getNamespace(String name) {
        return this.namespaces.stream().filter(ns -> StringUtils.equals(ns.getPrefix(), name)).map(ns -> ns.getUri()).findFirst();
    }
}

package it.doqui.libra.librabl.views.schema;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class TypedInterfaceDescriptor {
    private String name;
    private String title;
    private String parent;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean hidden;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean managed;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonProperty("mandatory-properties")
    private final Set<String> mandatoryProperties;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonProperty("suggested-properties")
    private final Set<String> suggestedProperties;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonProperty("mandatory-aspects")
    private final Set<String> mandatoryAspects;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private final Set<String> ancestors;

    public TypedInterfaceDescriptor() {
        this.mandatoryProperties = new HashSet<>();
        this.suggestedProperties = new HashSet<>();
        this.mandatoryAspects = new HashSet<>();
        this.ancestors = new LinkedHashSet<>();
    }

    public <T extends TypedInterfaceDescriptor> T copyTo(Class<T> clazz) {
        try {
            T descriptor = clazz.getDeclaredConstructor().newInstance();
            descriptor.setName(this.name);
            descriptor.setTitle(this.title);
            descriptor.setParent(this.parent);
            descriptor.setHidden(this.hidden);
            descriptor.setManaged(this.managed);
            descriptor.getMandatoryProperties().addAll(this.mandatoryProperties);
            descriptor.getSuggestedProperties().addAll(this.suggestedProperties);
            descriptor.getMandatoryAspects().addAll(this.mandatoryAspects);

            return descriptor;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

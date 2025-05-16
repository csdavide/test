package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputNodeRequest {

    @JsonProperty("type")
    private String typeName;

    public enum AspectOperation {
        ADD,
        REMOVE
    }

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final Set<String> aspects;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final Map<String,AspectOperation> aspectOperations;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final Map<String,Object> properties;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @JsonProperty("unmanaged-sgid")
    private Optional<String> unmanagedSgID;

    public InputNodeRequest() {
        this.aspects = new HashSet<>();
        this.aspectOperations = new HashMap<>();
        this.properties = new HashMap<>();
    }

    public void copy(InputNodeRequest x) {
        this.typeName = x.typeName;
        this.aspects.addAll(x.aspects);
        this.aspectOperations.putAll(x.aspectOperations);
        this.properties.putAll(x.properties);
    }
}

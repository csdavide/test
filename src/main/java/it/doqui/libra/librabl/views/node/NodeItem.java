package it.doqui.libra.librabl.views.node;

import com.fasterxml.jackson.annotation.*;
import it.doqui.libra.librabl.views.association.LinkItem;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeItem {
    private Long id;
    private String tenant;
    private String uuid;

    @JsonProperty("type")
    private String typeName;

    @JsonProperty("model")
    private String modelName;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ContentProperty> contents;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @JsonProperty("unmanaged-sgid")
    private Optional<String> unmanagedSgID;

    @JsonProperty("rights")
    private String rights;

    private TransactionInfo tx;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<String> aspects;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Map<String,Object> properties;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<LinkItem> parents;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<NodePathItem> paths;

    public NodeItem() {
        this.aspects = new HashSet<>();
        this.properties = new HashMap<>();
        this.parents = new ArrayList<>();
        this.contents = new ArrayList<>();
        this.paths = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeItem n)) return false;
        return Objects.equals(tenant, n.tenant) && Objects.equals(uuid, n.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, uuid);
    }


    @Getter
    @Setter
    @ToString
    public static class TransactionInfo {
        private Long id;
    }
}

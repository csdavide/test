package it.doqui.libra.librabl.application.model.graph;

import com.fasterxml.jackson.annotation.*;
import it.doqui.libra.librabl.domain.model.files.ContentProperty;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.application.model.association.LinkItem;
import it.doqui.libra.librabl.application.model.share.SharingItem;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.*;

import static it.doqui.libra.librabl.domain.model.graph.Constants.ASPECT_ECMSYS_PUBLIC;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeItem {
    private static final ZonedDateTime minusInfinite = ZonedDateTime.now().minusYears(1000);
    private static final ZonedDateTime plusInfinite = ZonedDateTime.now().plusYears(1000);

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

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<SharingItem> sharedLinks;

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
        this.sharedLinks = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeItem n)) return false;
        return Objects.equals(tenant, n.tenant) && Objects.equals(uuid, n.uuid);
    }

    @JsonIgnore
    public boolean isPublic() {
        var now = ZonedDateTime.now();
        return this.getAspects().contains(ASPECT_ECMSYS_PUBLIC)
            && getDate("ecm-sys:publicFrom", minusInfinite).isBefore(now)
            && getDate("ecm-sys:publicTo", plusInfinite).isAfter(now);
    }

    private ZonedDateTime getDate(String name, ZonedDateTime defaultValue) {
        return Optional.ofNullable(this.getProperties().get(name))
            .map(DateISO8601Utils::parseAsZonedDateTime)
            .orElse(defaultValue);
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

package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public final class ActiveNode extends AbstractNode {

    private String code;
    private String transactionFlags;

    @JsonIgnore
    private final List<Association> parents;

    @JsonIgnore
    private final List<Association> children;

    @JsonIgnore
    private final List<NodePath> paths;

    @JsonIgnore
    private final Map<String, Object> externalProperties;

    public ActiveNode() {
        super();
        parents = new ArrayList<>();
        children = new ArrayList<>();
        paths = new ArrayList<>();
        externalProperties = new HashMap<>();
    }

    public ActiveNode copy() {
        ActiveNode n = new ActiveNode();
        n.setVersion(0);
        n.setTenant(this.tenant);
        n.setUuid(UuidCreator.getTimeOrderedEpoch().toString());
        n.setTypeName(this.typeName);
        n.setUpdatedAt(this.updatedAt);
        n.setSecurityGroup(this.securityGroup);
        n.getExternalProperties().putAll(this.externalProperties);
        n.data.replaceWith(this.data);
        return n;
    }
}

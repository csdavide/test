package it.doqui.libra.librabl.business.provider.data.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.NodeDescriptor;
import it.doqui.libra.librabl.foundation.NodeReferenceable;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;

@Getter
@Setter
public class ActiveNode implements NodeReferenceable, NodeDescriptor, GraphNode {

    private Long id;
    private Integer version;
    private String tenant;
    private String uuid;
    private String typeName;
    private final NodeData data;
    private ZonedDateTime updatedAt;

    @JsonIgnore
    private final List<Association> parents;

    @JsonIgnore
    private final List<Association> children;

    @JsonIgnore
    private final List<NodePath> paths;

    private SecurityGroup securityGroup;
    private ApplicationTransaction tx;
    private String transactionFlags;

    public ActiveNode() {
        data = new NodeData();
        parents = new ArrayList<>();
        children = new ArrayList<>();
        paths = new ArrayList<>();
    }

    public ActiveNode copy() {
        ActiveNode n = new ActiveNode();
        n.setVersion(0);
        n.setTenant(this.tenant);
        n.setUuid(UUID.randomUUID().toString());
        n.setTypeName(this.typeName);
        n.setUpdatedAt(this.updatedAt);
        n.setSecurityGroup(this.securityGroup);
        n.data.replaceWith(this.data);
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActiveNode n = (ActiveNode) o;
        return Objects.equals(tenant, n.tenant) && Objects.equals(uuid, n.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, uuid);
    }

    @Override
    public URI getURI() {
        try {
            return new URI(String.format("%s://@%s@SpacesStore/%s", "workspace", tenant, uuid));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> getAspects() {
        return data.getAspects();
    }

    @Override
    public Map<String, Object> getProperties() {
        return data.getProperties();
    }
}

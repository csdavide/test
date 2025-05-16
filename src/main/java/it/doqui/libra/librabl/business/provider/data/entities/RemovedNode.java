package it.doqui.libra.librabl.business.provider.data.entities;

import it.doqui.libra.librabl.business.service.core.ApplicationTransaction;
import it.doqui.libra.librabl.foundation.NodeReferenceable;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

@Getter
@Setter
public class RemovedNode implements NodeReferenceable {

    private Long id;
    private String tenant;
    private String uuid;
    private final NodeData data;
    private boolean wipeable;
    private ApplicationTransaction tx;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemovedNode n = (RemovedNode) o;
        return Objects.equals(tenant, n.tenant) && Objects.equals(uuid, n.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, uuid);
    }

    @Override
    public URI getURI() {
        try {
            return new URI(String.format("%s://@%s@SpacesStore/%s", "lost", tenant, uuid));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public RemovedNode() {
        this.data = new NodeData();
    }
}

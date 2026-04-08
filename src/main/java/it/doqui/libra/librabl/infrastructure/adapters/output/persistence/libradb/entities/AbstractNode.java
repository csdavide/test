package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import it.doqui.libra.librabl.domain.model.files.ContentReferenceable;
import it.doqui.libra.librabl.domain.model.files.FileMetadata;
import it.doqui.libra.librabl.domain.model.graph.GraphNode;
import it.doqui.libra.librabl.domain.model.session.ApplicationTransaction;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;

@Getter
@Setter
public sealed class AbstractNode implements GraphNode permits ActiveNode, ArchivedNode {

    protected Long id;
    protected Integer version;
    protected String tenant;
    protected String uuid;
    protected String typeName;
    protected final NodeData data;
    protected ZonedDateTime updatedAt;
    protected SecurityGroup securityGroup;
    protected ApplicationTransaction tx;

    public AbstractNode() {
        data = new NodeData();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof AbstractNode n) {
            return Objects.equals(tenant, n.tenant) && Objects.equals(uuid, n.uuid);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, uuid);
    }

    @Override
    public Set<String> getAspects() {
        return data.getAspects();
    }

    @Override
    public Map<String, Object> getProperties() {
        return data.getProperties();
    }

    @Override
    public List<? extends FileMetadata> getContents() {
        return data.getContents();
    }

    @Override
    public Object getProperty(String name) {
        return data.getProperties().get(name);
    }

    @Override
    public boolean hasAspect(String name) {
        return data.hasAspect(name);
    }

    @Override
    public Optional<FileMetadata> getContent(URI contentUrl) {
        return Optional.ofNullable(data.getFileData(contentUrl));
    }

    @Override
    public Optional<FileMetadata> getContent(ContentReferenceable ref) {
        return Optional.ofNullable(data.getFileData(ref.getContentPropertyName(), ref.getFileName()));
    }
}

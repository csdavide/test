package it.doqui.libra.librabl.domain.model.graph;

import it.doqui.libra.librabl.domain.model.files.FileMetadata;
import it.doqui.libra.librabl.domain.model.files.FileProvider;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.SecurityGroup;

import java.util.List;

public interface GraphNode extends NodeDescriptor, NodeReferenceable, PropertyProvider, FileProvider {
    Long getId();
    Integer getVersion();
    SecurityGroup getSecurityGroup();
    List<? extends FileMetadata> getContents();
}

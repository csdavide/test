package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import it.doqui.libra.librabl.application.model.graph.VersionItem;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VersionDetails {
    private VersionItem item;
    private NodeData data;
}

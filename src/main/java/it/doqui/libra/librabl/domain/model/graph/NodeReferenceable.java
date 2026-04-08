package it.doqui.libra.librabl.domain.model.graph;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface NodeReferenceable {
    String getTenant();
    String getUuid();

    @JsonIgnore
    default boolean isNodeUndefined() {
        return getUuid() == null || getUuid().isBlank();
    }
}

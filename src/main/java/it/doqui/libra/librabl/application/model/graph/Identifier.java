package it.doqui.libra.librabl.application.model.graph;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface Identifier {
    @JsonProperty("uuid")
    String getUuid();
}

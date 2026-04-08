package it.doqui.libra.librabl.domain.model.graph;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum VertexType {
    @JsonProperty("id") @JsonAlias("ID") ID,
    @JsonProperty("uuid") @JsonAlias("UUID") UUID,
    @JsonProperty("path") @JsonAlias("PATH") PATH
}

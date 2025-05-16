package it.doqui.libra.librabl.views;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface Identifier {
    @JsonProperty("uuid")
    String getUuid();
}

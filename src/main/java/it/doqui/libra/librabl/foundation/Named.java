package it.doqui.libra.librabl.foundation;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface Named {
    @JsonProperty("name")
    String getName();
}

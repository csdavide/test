package it.doqui.libra.librabl.domain.model.files;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum ContentOperationMode {
    @JsonProperty("add") @JsonAlias("ADD") ADD,
    @JsonProperty("replace") @JsonAlias("REPLACE") REPLACE,
    @JsonProperty("remove") @JsonAlias("REMOVE") REMOVE
}

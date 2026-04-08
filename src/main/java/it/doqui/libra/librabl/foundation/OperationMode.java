package it.doqui.libra.librabl.foundation;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum OperationMode {
    @JsonProperty("sync") @JsonAlias("SYNC") SYNC,
    @JsonProperty("async") @JsonAlias("ASYNC") ASYNC,
    @JsonProperty("auto") @JsonAlias("AUTO") AUTO
}

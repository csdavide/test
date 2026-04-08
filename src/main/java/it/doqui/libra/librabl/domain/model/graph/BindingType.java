package it.doqui.libra.librabl.domain.model.graph;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum BindingType {
    @JsonProperty("hard") @JsonAlias("HARD") HARD,
    @JsonProperty("soft") @JsonAlias("SOFT") SOFT,
    @JsonProperty("loose") @JsonAlias("LOOSE") LOOSE;

    @JsonIgnore
    public static BindingType getBindingType(Boolean hard) {
        return hard == null ? LOOSE : (hard ? HARD : SOFT);
    }
}

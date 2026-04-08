package it.doqui.libra.librabl.application.model.properties;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public final class PropertyValueOperation implements PropertyObject {
    private PropertyValueOperationType op;
    private String key;
    private Object value;

    public enum PropertyValueOperationType {
        @JsonProperty("pop") @JsonAlias("POP") POP,
        @JsonProperty("push") @JsonAlias("PUSH") PUSH,
        @JsonProperty("remove") @JsonAlias("REMOVE") REMOVE,
        @JsonProperty("put") @JsonAlias("PUT") PUT,
        @JsonProperty("insert") @JsonAlias("INSERT") INSERT,
        @JsonProperty("add") @JsonAlias("ADD") ADD,
        @JsonProperty("append") @JsonAlias("APPEND") APPEND,
        @JsonProperty("multi") @JsonAlias("MULTI") MULTI
    }
}

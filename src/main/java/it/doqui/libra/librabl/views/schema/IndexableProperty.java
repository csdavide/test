package it.doqui.libra.librabl.views.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

public interface IndexableProperty {
    String getName();
    String getType();
    boolean isIndexed();
    boolean isReverseTokenized();
    boolean isTokenized();

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    default boolean isMultiple() {
        return false;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    default boolean isStored() {
        return false;
    }

    default String getTokenizationType() {
        return null;
    }
}

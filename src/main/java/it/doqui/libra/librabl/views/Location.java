package it.doqui.libra.librabl.views;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface Location {

    @JsonProperty("url")
    String getUrl();
}

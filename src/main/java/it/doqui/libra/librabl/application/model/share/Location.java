package it.doqui.libra.librabl.application.model.share;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface Location {

    @JsonProperty("url")
    String getUrl();
}

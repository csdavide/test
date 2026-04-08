package it.doqui.libra.librabl.application.model.rendition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenditionSettings {
    private boolean isRenditionableTempNode = false;
    private boolean isTransformerTempNode = false;
    private boolean isRenditionTempNode = false;
    private LinkedInputNodeRequest newRenditionInputRequest = null;

    @JsonIgnore
    private boolean forceGeneration = false;

    @JsonIgnore
    private Boolean unwrap = null;

    @JsonIgnore
    private String resultMimetype = null;
}

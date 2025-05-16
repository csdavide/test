package it.doqui.libra.librabl.views.renditions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenditionNode {

    private String uuid;
    private String description;
    private String mimeType;
    private Boolean generated;

    @JsonIgnore
    private byte[] binaryData;
}

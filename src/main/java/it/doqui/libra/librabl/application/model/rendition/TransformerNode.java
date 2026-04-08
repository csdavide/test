package it.doqui.libra.librabl.application.model.rendition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TransformerNode {

    private String uuid;
    private String description;
    private String genMimeType;
    private String mimeType;
    private String hash;

    @JsonIgnore
    private byte[] binaryData;
}

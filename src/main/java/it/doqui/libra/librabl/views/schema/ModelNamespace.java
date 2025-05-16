package it.doqui.libra.librabl.views.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.net.URI;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelNamespace {
    private String prefix;
    private URI uri;
}

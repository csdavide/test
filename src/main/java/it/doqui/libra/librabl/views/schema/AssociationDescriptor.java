package it.doqui.libra.librabl.views.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssociationDescriptor {
    private String name;

    @JsonProperty("parent")
    @JsonAlias("source")
    private String parent;

    @JsonProperty("child")
    @JsonAlias("target")
    private String child;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean light;
}

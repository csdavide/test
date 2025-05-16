package it.doqui.libra.librabl.views.association;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssociationItem {
    private Long id;

    @JsonProperty("type")
    private String typeName;
    private String name;
    private Boolean hard;

    @JsonProperty("parent")
    @JsonAlias("source")
    private String parent;

    @JsonProperty("child")
    @JsonAlias("target")
    private String child;

    public boolean isHard() {
        return hard != null && hard;
    }

    @JsonProperty("childAssociation")
    public boolean isChildAssociation() {
        return hard != null;
    }

}

package it.doqui.libra.librabl.domain.model.graph;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParentLink {
    private Vertex parent;
    private String name;
    private String type;
    private BindingType bindingType;

    @JsonIgnore
    public Boolean getHard() {
        return bindingType == null ? null : switch (bindingType) {
            case HARD -> true;
            case SOFT -> false;
            case LOOSE -> null;
        };
    }
}

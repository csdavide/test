package it.doqui.libra.librabl.application.model.statements;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RenameStatement {
    public enum RenameMode {
        SPECIFIC_PARENT,
        ALL_PARENTS,
        FIRST_PARENT,
        ALL_HARD_PARENTS
    }

    private Vertex parent;
    private String name;
    private RenameMode renameMode;
    private String propertyName;
}

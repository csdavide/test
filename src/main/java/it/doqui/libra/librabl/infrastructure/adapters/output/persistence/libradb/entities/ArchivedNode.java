package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public final class ArchivedNode extends AbstractNode {

    @JsonIgnore
    private List<ArchivedAssociation> parents;

    public ArchivedNode() {
        super();
        parents = new ArrayList<>();
    }
}

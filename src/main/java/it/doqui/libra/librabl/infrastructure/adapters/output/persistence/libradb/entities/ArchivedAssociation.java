package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ArchivedAssociation {

    private long id;
    private String archivedParentUuid;
    private String activeParentUuid;
    private String childUuid;
    private long parentId;
    private long childId;
    private String typeName;
    private String name;
    private String code;
    private boolean hard;

}

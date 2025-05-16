package it.doqui.libra.librabl.business.provider.data.entities;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Association {

    private Long id;
    private ActiveNode parent;
    private ActiveNode child;
    private String typeName;
    private String name;
    private String code;
    private Boolean hard;

    public boolean isHard() {
        return hard != null && hard;
    }
}

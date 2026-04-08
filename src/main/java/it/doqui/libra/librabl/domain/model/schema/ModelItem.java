package it.doqui.libra.librabl.domain.model.schema;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
//TODO: review required
public class ModelItem {
    private String tenant;
    private String name;
    private String data;
    private String format;
    private boolean active;
}

package it.doqui.libra.librabl.business.service.schema;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ModelItem {
    private String tenant;
    private String name;
    private String data;
    private String format;
    private boolean active;
}

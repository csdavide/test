package it.doqui.libra.librabl.views.document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentParameters {
    private String uuid;
    private String name;
    private String contentPropertyName;
    private boolean storeRequired;
    private boolean returnRequired;
}

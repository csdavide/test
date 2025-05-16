package it.doqui.libra.librabl.views.document;

import it.doqui.libra.librabl.views.OperationMode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SealRequest {

    private SealParams sealParams;
    private OperationMode mode;
    private Long timeout;

}

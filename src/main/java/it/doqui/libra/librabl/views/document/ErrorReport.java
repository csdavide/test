package it.doqui.libra.librabl.views.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ErrorReport {

    private SignOperation operation;
    private String error;

}

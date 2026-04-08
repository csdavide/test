package it.doqui.libra.librabl.application.model.document;

import it.doqui.libra.librabl.domain.model.document.DocumentOperation;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ErrorReport {

    private DocumentOperation operation;
    private String error;

}

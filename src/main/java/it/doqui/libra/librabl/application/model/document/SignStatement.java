package it.doqui.libra.librabl.application.model.document;

import it.doqui.libra.librabl.domain.model.files.ContentRef;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Accessors(chain = true)
public class SignStatement {
    private ContentRef documentRef;
    private SignParams signParams;
    private StoreParams storeParams;
}

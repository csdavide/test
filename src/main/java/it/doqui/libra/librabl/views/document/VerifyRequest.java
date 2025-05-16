package it.doqui.libra.librabl.views.document;

import it.doqui.libra.librabl.api.v2.rest.dto.document.DocumentVerificationParameters;
import it.doqui.libra.librabl.views.node.ContentRef;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyRequest extends DocumentVerificationParameters {

    private ContentRef document;
    private ContentRef detachedDocument;

}

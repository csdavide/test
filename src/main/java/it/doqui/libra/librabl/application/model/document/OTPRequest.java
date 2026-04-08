package it.doqui.libra.librabl.application.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.document.DocumentOperation;
import it.doqui.libra.librabl.domain.model.document.Provider;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OTPRequest {

    DocumentOperation scope = DocumentOperation.SIGN;
    Provider provider = Provider.UANATACA_BOX;
    String username;
    String password;
    String pin;
    int uses = 1; //serve per UANATACA_CLOUD
}

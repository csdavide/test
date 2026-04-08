package it.doqui.libra.librabl.application.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.document.Provider;
import it.doqui.libra.librabl.domain.model.document.SignType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class SignParams {

    private SignType signType;
    private Provider provider = Provider.UANATACA_BOX;
    private String username;
    private String password;
    private String pin;
    private String otp;
    private String tsUsername;
    private String tsPassword;
    private String tsUrl;

    //GATEFIRE
    private String cf;
    private String collocation;
}

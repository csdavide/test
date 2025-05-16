package it.doqui.libra.librabl.views.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SealParams {

    private String type;
    private String delegatedDomain;
    private String delegatedPassword;
    private String delegatedUser;
    private String environmentId;
    private String user;
    private String otpPassword;
    private String typeOtpAuth;
    private String typeHSM;

    @JsonIgnore
    private boolean storeResult;

}

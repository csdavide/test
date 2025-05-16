package it.doqui.libra.librabl.views.document;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class CertificateParams {

    private ZonedDateTime verificationDate;
    private int verificationType;
    private int verificationScope;
    private int profileType;

    public CertificateParams(int verificationScope, int verificationType, int profileType) {
        this.verificationScope = verificationScope;
        this.verificationType = verificationType;
        this.profileType = profileType;
        this.verificationDate = ZonedDateTime.now();
    }
}

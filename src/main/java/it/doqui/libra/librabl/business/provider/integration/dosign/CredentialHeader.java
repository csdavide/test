package it.doqui.libra.librabl.business.provider.integration.dosign;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CredentialHeader {
    private String clientId;
    private String customer;

    public String toJsonString() {
        return "{\"clientId\":\"librabl@" + clientId + "\",\"customer\":\"" + customer + "\"}";
    }
}

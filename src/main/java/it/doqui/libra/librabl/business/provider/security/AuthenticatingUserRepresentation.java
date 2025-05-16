package it.doqui.libra.librabl.business.provider.security;

import it.doqui.libra.librabl.business.provider.data.entities.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
@ToString
class AuthenticatingUserRepresentation {
    private User user;
    private String publicKey;
    private final Set<String> scopes;

    AuthenticatingUserRepresentation() {
        this.scopes = new HashSet<>();
    }
}

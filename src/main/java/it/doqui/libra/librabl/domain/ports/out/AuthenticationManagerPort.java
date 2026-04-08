package it.doqui.libra.librabl.domain.ports.out;

import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.application.model.user.PkItem;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface AuthenticationManagerPort {
    Collection<PkItem> listPublicKeys();
    PkItem addOrUpdatePublicKey(PkItem Item);
    boolean deletePublicKey(String kid);
    void loginAsGuest();
    UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password);
    UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password, SessionMode mode);
    UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password, Set<String> additionalRoles, SessionMode mode);
    UserContext authenticateWithBasicCredentials(String encodedCredentials);
    UserContext authenticateWithToken(String token, String authority);
    UserContext authenticateWithToken(String token, String authority, ZonedDateTime time, SessionMode mode);
    void authenticateUsingPK(TenantRef tenantRef, String pubKey, Collection<String> scopes);
    void authenticateUserOnBehalfOf(AuthorityRef authorityRef, UserContext currentUserContext);
    TenantRef autenticateIfRequired(TenantRef tenantRef, boolean adminRequired);
}

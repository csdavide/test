package it.doqui.libra.librabl.business.service.auth;

import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.views.security.PkItem;
import it.doqui.libra.librabl.views.security.PkRequest;

import java.util.Collection;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface AuthenticationService {
    Collection<PkItem> listPublicKeys();
    PkItem addPublicKey(PkRequest request);
    PkItem updatePublicKey(PkItem item);
    boolean deletePublicKey(String kid);
    UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password);
    UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password, UserContext.Mode mode);
    void authenticateUsingPK(TenantRef tenantRef, String pubKey, Collection<String> scopes);
    void authenticateUserOnBehalfOf(AuthorityRef authorityRef, UserContext currentUserContext);
    TenantRef autenticateIfRequired(TenantRef tenantRef, boolean adminRequired);
}

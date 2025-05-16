package it.doqui.libra.librabl.business.provider.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.IncorrectClaimException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import it.doqui.libra.librabl.business.provider.data.entities.User;
import it.doqui.libra.librabl.business.provider.data.entities.UserGroup;
import it.doqui.libra.librabl.business.provider.integration.messaging.events.CleanCacheEvent;
import it.doqui.libra.librabl.business.provider.multitenant.TenantDataManager;
import it.doqui.libra.librabl.business.service.auth.AuthenticationService;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.TemporaryService;
import it.doqui.libra.librabl.cache.LRUCache;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.Expirable;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.security.PkItem;
import it.doqui.libra.librabl.views.security.PkRequest;
import it.doqui.libra.librabl.views.tenant.TenantSpace;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.Claims;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;

import static it.doqui.libra.librabl.business.service.auth.UserContext.*;

@ApplicationScoped
@Slf4j
public class AuthenticationManager implements AuthenticationService {

    @ConfigProperty(name = "libra.authentication.cache.size", defaultValue = "1000")
    int cacheSize;

    @ConfigProperty(name = "libra.authentication.cache.max-cached-gateway-accounts", defaultValue = "1")
    int maxCachedAccount;

    @ConfigProperty(name = "libra.authentication.cache.application-time", defaultValue = "1h")
    Duration appExpiryTime;

    @ConfigProperty(name = "libra.authentication.default-alg", defaultValue = "CLEAR")
    String defaultAlg;

    @ConfigProperty(name = "libra.authentication.sysadmin.kid", defaultValue = "sys")
    String sysKid;

    @ConfigProperty(name = "libra.authentication.sysadmin.pub_key")
    Optional<String> sysKey;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @Inject
    SecurityDAO securityDAO;

    @Inject
    TenantDataManager tenantManager;

    @Inject
    TemporaryService temporaryService;

    private LRUCache<String,Expirable<UserContextImpl>> contextCache;
    private LRUCache<String,Expirable<AccountDTO.AccountDataDTO>> accountCache;

    @PostConstruct
    void init() {
        contextCache = new LRUCache<>(cacheSize);
        accountCache = new LRUCache<>(maxCachedAccount);
    }

    void onStart(@Observes CleanCacheEvent ev) {
        contextCache.clear();
        accountCache.clear();
        log.info("Authentication caches cleaned");
    }

    public void loginAsAdmin(UserContext currentUserContext) {
        var ctx = loginAsAdmin(currentUserContext.getTenantRef(), currentUserContext.getDbSchema());
        ctx.setApiLevel(currentUserContext.getApiLevel());
        ctx.setApplication(currentUserContext.getApplication());
        ctx.setUserIdentity(currentUserContext.getUserIdentity());
    }

    public UserContextImpl loginAsAdmin(TenantRef tenantRef, String schema) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setAuthorityRef(new AuthorityRef("admin", tenantRef));
        userContext.setDbSchema(schema);
        userContext.addRole(ROLE_ADMIN);
        userContext.addRole(ROLE_USER);
        userContext.addScope(SCOPE_DEFAULT);
        UserContextManager.setContext(userContext);
        log.debug("Logged as admin {}", userContext);
        return userContext;
    }

    @Override
    public Collection<PkItem> listPublicKeys() {
        var ctx = UserContextManager.getContext();
        return securityDAO.listPublicKeys(ctx.getTenantRef(), ctx.getDbSchema());
    }

    @Override
    public PkItem addPublicKey(PkRequest request) {
        var item = new PkItem();
        item.setKid(UUID.randomUUID().toString());
        item.setKey(request.getKey());
        item.setUsername(request.getUsername());
        item.getScopes().addAll(request.getScopes());

        return updatePublicKey(item);
    }

    @Override
    public PkItem updatePublicKey(PkItem item) {
        var normalizedKey = item.getKey()
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replaceAll("\r\n?|\n", "")
            .replace("-----END PUBLIC KEY-----", "");
        item.setKey(normalizedKey);

        var ctx = UserContextManager.getContext();
        securityDAO.persistPublicKey(ctx.getTenantRef(), ctx.getDbSchema(), item);
        return item;
    }

    @Override
    public boolean deletePublicKey(String kid) {
        var ctx = UserContextManager.getContext();
        return securityDAO.deletePublicKey(ctx.getTenantRef(), ctx.getDbSchema(), kid);
    }

    @Override
    public UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password) {
        return authenticateUser(authorityRef, password, Mode.SYNC);
    }

    @Override
    public UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password, Mode mode) {
        final UserContextImpl ctx;
        var pw = Optional.ofNullable(password).flatMap(x -> x).orElse(null);
        if (pw == null) {
            var cacheKey = "Internal " + authorityRef.toString();
            var cacheValue = contextCache.get(cacheKey);
            if (cacheValue.isPresent() && !cacheValue.get().isExpired()) {
                var userContext = cacheValue.get().getObject();
                userContext.regenerateID();
                UserContextManager.setContext(userContext);
                log.debug("Account {} logged in with roles {} using cached internal context expiring at {}", userContext.getAuthorityRef(), userContext.getRoleSet(), cacheValue.get().getExpires());
                return userContext;
            }

            ctx = authenticate(authorityRef, password, null, mode);
            contextCache.put(cacheKey, new Expirable<>(ctx, ZonedDateTime.now().plusSeconds(appExpiryTime.toSeconds())));
        } else {
            var credentials = authorityRef.toString() + ":" + pw;
            var encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            ctx = authenticateWithBasicCredentials(encodedCredentials);
        }

        ctx.setMode(mode);
        return ctx;
    }

    @Override
    public void authenticateUsingPK(TenantRef tenantRef, String pubKey, Collection<String> scopes) {
        TenantSpace t = tenantManager.findByIdOptional(tenantRef.toString())
            .filter(x -> StringUtils.isNotBlank(x.getSchema()))
            .orElseThrow(UnauthorizedException::new);

        tenantRef = TenantRef.valueOf(t.getTenant());

        var authenticatedUser = securityDAO.findUserByPubKey(pubKey, tenantRef.toString(), t.getSchema())
            .filter(au -> BooleanUtils.isTrue(au.getUser().getData().getEnabled()))
            .filter(au -> !au.getUser().getData().isLocked())
            .orElseThrow(UnauthorizedException::new);

        login(t, authenticatedUser, scopes);
    }

    UserContextImpl login(TenantSpace t, AuthenticatingUserRepresentation authenticatedUser, Collection<String> scopes) {
        for (String scope : scopes) {
            if (!authenticatedUser.getScopes().contains(scope)) {
                throw new UnauthorizedException("Account not available for scope " + scope);
            }
        }

        var user = authenticatedUser.getUser();
        UserContextImpl userContext = new UserContextImpl();
        userContext.setDbSchema(t.getSchema());
        userContext.getAttributes().put(TENANT_DATA_ATTR, t.getData());

        userContext.setAuthorityRef(new AuthorityRef(user.getUsername(), TenantRef.valueOf(t.getTenant())));
        userContext.addRole(ROLE_USER);
        if (StringUtils.equalsIgnoreCase(user.getUsername(), "admin")) {
            userContext.addRole(ROLE_ADMIN);
        }
        if (authenticatedUser.getScopes().contains("sysadmin")) {
            userContext.addRole(ROLE_SYSADMIN);
        }
        if (authenticatedUser.getScopes().contains("pu")) {
            userContext.addRole(ROLE_POWERUSER);
        }
        if (authenticatedUser.getScopes().contains("pa")) {
            userContext.addRole(ROLE_POWERADMIN);
        }

        user.getData().getRoles().forEach(userContext::addRole);
        user.getGroups().stream().map(UserGroup::getGroupname).forEach(userContext::addGroup);
        authenticatedUser.getScopes().forEach(userContext::addScope);


        UserContextManager.setContext(userContext);
        log.debug("Security Context {} set with roles {}", userContext.getAuthorityRef(), userContext.getRoleSet());
        return userContext;
    }

    @Override
    public void authenticateUserOnBehalfOf(AuthorityRef authorityRef, UserContext currentUserContent) {
        Predicate<User> predicate = StringUtils.equalsIgnoreCase(authorityRef.getTenantRef().toString(), temporaryService.getTemporaryTenant())
            ? (User u) -> true
            : (User u) -> u.getData().getAuthorizedGuests().contains(currentUserContent.getAuthority());
        //noinspection OptionalAssignedToNull
        var ctx = authenticate(authorityRef, null, predicate, Mode.SYNC);
        ctx.setApiLevel(currentUserContent.getApiLevel());
        ctx.setApplication(currentUserContent.getApplication());
        ctx.setUserIdentity(currentUserContent.getUserIdentity());
        if (currentUserContent.getRoleSet() != null) {
            currentUserContent.getRoleSet().forEach(ctx::addRole);
        }
    }

    @Override
    public TenantRef autenticateIfRequired(TenantRef tenantRef, boolean adminRequired) {
        var authorityRef = UserContextManager.getContext().getAuthorityRef();
        if (StringUtils.equalsIgnoreCase(authorityRef.getTenantRef().toString(), tenantRef.toString())
            && (!adminRequired || UserContextManager.getContext().isUserInRole(UserContext.ROLE_ADMIN))) {
            tenantRef = authorityRef.getTenantRef();
        } else if (UserContextManager.getContext().isUserInRole(UserContext.ROLE_SYSADMIN)) {
            log.debug("Login as admin having roles {}", UserContextManager.getContext().getRoleSet());
            var _ctx = UserContextManager.getContext();
            //noinspection OptionalAssignedToNull
            var ctx = authenticateUser(new AuthorityRef("admin", tenantRef), null, UserContext.Mode.SYNC);
            ctx.setApiLevel(_ctx.getApiLevel());
            ctx.setApplication(_ctx.getApplication());
            ctx.setChannel(_ctx.getChannel());
            tenantRef = ctx.getTenantRef();
        } else if (StringUtils.equalsIgnoreCase(authorityRef.getTenantRef().toString(), tenantRef.toString())) {
            throw new ForbiddenException("Admin permissions required on tenant " + tenantRef);
        } else {
            throw new ForbiddenException("Cannot access tenant " + tenantRef);
        }

        return tenantRef;
    }

    private String subject(String innerSubject, String authority) {
        String subject = null;
        if (authority != null) {
            if (innerSubject == null) {
                subject = authority;
            } else {
                var authorityRef = AuthorityRef.valueOf(innerSubject);
                var alternativeRef = AuthorityRef.valueOf(authority);
                if (StringUtils.equals(authorityRef.getIdentity(), "admin") || StringUtils.equalsIgnoreCase(authorityRef.getIdentity(), alternativeRef.getIdentity())) {
                    if (StringUtils.equalsIgnoreCase(alternativeRef.getTenantRef().toString(), authorityRef.getTenantRef().toString())) {
                        subject = authority;
                    }
                }

                if (subject == null) {
                    throw new ForbiddenException("Incompatible authority");
                }
            }
        }

        if (subject == null) {
            subject = innerSubject;
        }

        return subject;
    }

    UserContextImpl authenticate(AuthorityRef authorityRef, Optional<String> password, Predicate<User> userPredicate, Mode mode) {
        log.debug("Authenticating user {}", authorityRef);
        var tenantName = authorityRef.getTenantRef().toString();
        TenantSpace t = tenantManager.findByIdOptional(tenantName)
            .filter(x -> x.getData().isEnabled())
            .filter(x -> StringUtils.isNotBlank(x.getSchema()))
            .orElseThrow(() -> new UnauthorizedException("Tenant not found: " + tenantName));

        authorityRef = new AuthorityRef(authorityRef.getIdentity(), TenantRef.valueOf(t.getTenant()));

        log.debug("Looking for user {} in schema {}", authorityRef, t.getSchema());
        var identity = authorityRef.getIdentity();
        var user = securityDAO.findUser(authorityRef, t.getSchema())
            .filter(u -> BooleanUtils.isTrue(u.getData().getEnabled()))
            .filter(u -> !u.getData().isLocked())
            .filter(u ->
                Optional.ofNullable(password).isEmpty() ||
                match(
                    Optional.of(password).flatMap(x -> x).orElse(null),
                    u.getData().getPassword(),
                    Objects.requireNonNullElse(u.getData().getAlg(), defaultAlg)))
            .filter(u -> userPredicate == null || userPredicate.test(u))
            .orElse(null);

        if (user == null) {
            if (StringUtils.equalsIgnoreCase(identity, "admin")
                && Optional.ofNullable(password).isEmpty()
                && (
                mode == Mode.ASYNC
                    || UserContextManager.getContext().isUserInRole(UserContext.ROLE_SYSADMIN)
                    || StringUtils.equals(tenantName, UserContextManager.getTenant()))
            ) {
                var userContext = loginAsAdmin(authorityRef.getTenantRef(), t.getSchema());
                userContext.setDbSchema(t.getSchema());
                userContext.getAttributes().put(TENANT_DATA_ATTR, t.getData());
                userContext.setMode(mode);
                return userContext;
            }

            throw new UnauthorizedException();
        }

        var authenticatedUser = new AuthenticatingUserRepresentation();
        authenticatedUser.setUser(user);
        authenticatedUser.getScopes().add(SCOPE_DEFAULT);
        var ctx = login(t, authenticatedUser, Set.of());
        ctx.setMode(mode);
        return ctx;
    }

    UserContextImpl authenticateWithToken(String token, String authority) {
        var cacheKey = token + Optional.ofNullable(authority).map(a -> "-" + a).orElse("");
        var cacheValue = contextCache.get(cacheKey);
        if (cacheValue.isPresent()) {
            if (!cacheValue.get().isExpired()) {
                var userContext = cacheValue.get().getObject();
                userContext.regenerateID();
                UserContextManager.setContext(userContext);
                log.debug("Account {} logged in with roles {} using cached JWT expiring at {}", userContext.getAuthorityRef(), userContext.getRoleSet(), cacheValue.get().getExpires());
                return userContext;
            }
        }

        DecodedJWT jwt = JWT.decode(token);
        if (jwt.getExpiresAtAsInstant() == null) {
            throw new UnauthorizedException("Unsupported token without expiration");
        } else if (jwt.getExpiresAtAsInstant().isBefore(Instant.now())) {
            throw new UnauthorizedException("Token expired");
        } else if (jwt.getIssuedAtAsInstant() != null && jwt.getIssuedAtAsInstant().isAfter(Instant.now())) {
            throw new UnauthorizedException("Token not valid yet");
        }

        var subject = subject(jwt.getSubject(), authority);
        if (StringUtils.isBlank(subject)) {
            throw new UnauthorizedException("No authority specified");
        }

        final UserContextImpl result;
        if (sysKey.isPresent() && StringUtils.equals(jwt.getKeyId(), sysKid)) {
            log.debug("Authenticating using system key");
            try {
                var normalizedKey = sysKey.get()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replaceAll("\r\n?|\n", "")
                    .replace("-----END PUBLIC KEY-----", "");

                try {
                    validateJWT(jwt, normalizedKey);
                } catch (RuntimeException e) {
                    log.warn("Validation exception {}: provided token '{}', using key '{}'", e.getMessage(), jwt.getToken(), normalizedKey);
                    throw e;
                }

                if (StringUtils.equals(subject, "sysmon")) {
                    var ctx = authenticate(AuthorityRef.valueOf("admin@default"), null, null, Mode.SYNC);
                    result = new UserContextImpl();
                    result.setAuthorityRef(ctx.getAuthorityRef());
                    result.setDbSchema(ctx.getDbSchema());
                    result.addRole(ROLE_SYSMON);
                    UserContextManager.setContext(result);
                } else {
                    var aud = jwt.getAudience();
                    if (aud != null) {
                        subject = aud.stream().findFirst().orElse(subject);
                    }

                    if (StringUtils.equals(subject, "sysadmin")) {
                        result = new UserContextImpl();
                        result.setAuthorityRef(AuthorityRef.valueOf("sysadmin"));
                        result.setDbSchema(masterSchema);
                        UserContextManager.setContext(result);
                    } else {
                        //noinspection OptionalAssignedToNull
                        result = authenticate(AuthorityRef.valueOf(subject), null, null, Mode.SYNC);
                    }

                    result.addRole(ROLE_SYSADMIN);
                }
            } catch (UnauthorizedException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Unable to validate system key: {}", e.getMessage());
                throw new UnauthorizedException();
            }
        } else {
            var requestedAuthorityRef = AuthorityRef.valueOf(subject);
            var tenantName = requestedAuthorityRef.getTenantRef().toString();
            TenantSpace t = tenantManager.findByIdOptional(tenantName)
                .filter(x -> StringUtils.isNotBlank(x.getSchema()))
                .orElseThrow(() -> new UnauthorizedException("Tenant not found: " + tenantName));

            var authorityRef = new AuthorityRef(requestedAuthorityRef.getIdentity(), TenantRef.valueOf(t.getTenant()));
            result = securityDAO
                .findUserByKid(jwt.getKeyId(), authorityRef, t.getSchema())
                .map(a -> {
                    try {
                        validateJWT(jwt, a.getPublicKey());

                        var aud = jwt.getAudience();
                        if (aud != null && !aud.isEmpty()) {
                            var target = AuthorityRef.valueOf(aud.stream().findFirst().orElse(null));
                            if (!StringUtils.equalsIgnoreCase(authorityRef.toString(), target.toString())) {
                                if (!a.getScopes().contains(SCOPE_SYSADMIN)) {
                                    throw new UnauthorizedException("Account not available for scope " + SCOPE_SYSADMIN);
                                }

                                log.debug("Got a sysadmin key. Authenticating on behalf of {}", target);
                                //noinspection OptionalAssignedToNull
                                var ctx = authenticate(target, null, null, Mode.SYNC);
                                ctx.addRole(ROLE_SYSADMIN);
                                a.getUser().getData().getRoles().forEach(ctx::addRole);
                                return ctx;
                            }
                        }

                        return login(t, a, Set.of(SCOPE_DEFAULT));
                    } catch (SignatureVerificationException | IncorrectClaimException | TokenExpiredException e) {
                        log.error("Token validation failed having iss {} kid {}: {}", jwt.getIssuer(), jwt.getKeyId(), e.getMessage());
                        throw new UnauthorizedException();
                    } catch (WebException e) {
                        log.error("Login failed ({}): {}", e.getCode(), e.getMessage());
                        log.error(e.getMessage(), e);
                        throw e;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        throw new UnauthorizedException(e.getMessage());
                    }
                })
                .orElseThrow(UnauthorizedException::new);
        }

        result.setApplication(jwt.getIssuer());
        result.setUserIdentity(jwt.getClaim(Claims.preferred_username.name()).asString());
        result.setAuthenticationScheme(SecurityContext.CLIENT_CERT_AUTH);
        log.debug("Account {} logged in with roles {} using JWT", result.getAuthorityRef(), result.getRoleSet());
        contextCache.put(cacheKey, new Expirable<>(result, ZonedDateTime.ofInstant(jwt.getExpiresAtAsInstant(), ZoneId.systemDefault())));
        return result;
    }

    private void validateJWT(DecodedJWT jwt, String encodedKey) throws Exception {
        var algorithm = Algorithm.RSA256((RSAPublicKey) getPublicKey(encodedKey), null);
        var verifier = JWT.require(algorithm).build();
        verifier.verify(jwt);
        log.debug("Token validated having iss {} kid {}. It will expires at {}", jwt.getIssuer(), jwt.getKeyId(), jwt.getExpiresAtAsInstant());
    }

    private PublicKey getPublicKey(String encodedKey) throws Exception {
        var sb = new StringBuilder();
        try (var reader = new BufferedReader(new StringReader(encodedKey))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("---")) {
                    sb.append(line);
                }
            }
        }

        byte[] byteKey = Base64.getDecoder().decode(sb.toString().getBytes(StandardCharsets.UTF_8));
        X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(byteKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        return kf.generatePublic(X509publicKey);
    }

    UserContextImpl authenticateWithBasicCredentials(String encodedCredentials) {
        var cacheKey = "Basic " + encodedCredentials;
        var cacheValue = contextCache.get(cacheKey);
        if (cacheValue.isPresent()) {
            if (!cacheValue.get().isExpired()) {
                var userContext = cacheValue.get().getObject();
                userContext.regenerateID();
                UserContextManager.setContext(userContext);
                log.debug("Account {} logged in with roles {} using cached credentials expiring at {}", userContext.getAuthorityRef(), userContext.getRoleSet(), cacheValue.get().getExpires());
                return userContext;
            }
        }

        var s = new String(java.util.Base64.getDecoder().decode(encodedCredentials)).split(":");
        var username = s[0];
        var password = s[1];

        var authorityRef = AuthorityRef.valueOf(username);
        var userContext = authenticate(authorityRef, Optional.ofNullable(password), null, Mode.SYNC);
        userContext.setAuthenticationScheme(SecurityContext.BASIC_AUTH);

        log.debug("Account {} logged using basic credentials", userContext.getAuthorityRef());
        contextCache.put(cacheKey, new Expirable<>(userContext, ZonedDateTime.now().plusSeconds(appExpiryTime.toSeconds())));
        return userContext;
    }

    private boolean match(String providedPassword, String expectedPassword, String usingAlg) {
        if (StringUtils.isBlank(expectedPassword) || StringUtils.isBlank(providedPassword)) {
            return false;
        }

        try {
            var myHash = ObjectUtils.hash(providedPassword, usingAlg);
            var match = StringUtils.equals(expectedPassword, myHash);
            if (!match) {
                log.debug("Password does not match: calculated {} using {}, expected {}", myHash, usingAlg, expectedPassword);
            }
            return match;
        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to hash password using ALG {}: {}", usingAlg, e.getMessage());
            return false;
        }
    }
}

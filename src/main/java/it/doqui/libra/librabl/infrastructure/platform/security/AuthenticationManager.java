package it.doqui.libra.librabl.infrastructure.platform.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.IncorrectClaimException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.f4b6a3.uuid.UuidCreator;
import it.doqui.libra.librabl.application.model.user.PkItem;
import it.doqui.libra.librabl.application.ports.in.TemporaryUseCase;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantSpace;
import it.doqui.libra.librabl.domain.ports.out.AuthenticationManagerPort;
import it.doqui.libra.librabl.domain.ports.out.TenantRepository;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.Expirable;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.ForbiddenException;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.User;
import it.doqui.libra.librabl.infrastructure.adapters.output.persistence.libradb.entities.UserGroup;
import it.doqui.libra.librabl.infrastructure.platform.events.CleanCacheEvent;
import it.doqui.libra.librabl.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.doqui.libra.librabl.domain.model.session.UserContext.*;

@ApplicationScoped
@Slf4j
public class AuthenticationManager implements AuthenticationManagerPort {

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

    @ConfigProperty(name = "libra.authentication.cache.ttl", defaultValue = "12h")
    Duration cacheTTL;

    @ConfigProperty(name = "libra.multitenant.master-schema")
    String masterSchema;

    @Inject
    SecurityDAO securityDAO;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    TemporaryUseCase temporaryUseCase;

    @Inject
    SessionContextHolder sessionContext;

    private Cache<String,Expirable<UserContext>> contextCache;
    private Cache<String,Expirable<AccountDTO.AccountDataDTO>> accountCache;

    @PostConstruct
    void init() {
        contextCache = Caffeine.newBuilder().maximumSize(cacheSize).expireAfterWrite(cacheTTL).recordStats().build();
        accountCache = Caffeine.newBuilder().maximumSize(maxCachedAccount).expireAfterWrite(cacheTTL).recordStats().build();
    }

    void onStart(@Observes CleanCacheEvent ev) {
        contextCache.invalidateAll();
        accountCache.invalidateAll();
        log.info("Authentication caches cleaned");
    }

    public void loginAsAdmin(UserContext currentUserContext) {
        loginAsAdmin(currentUserContext.getTenantRef(), currentUserContext.getDbSchema());
    }

    public UserContextImpl loginAsAdmin(TenantRef tenantRef, String schema) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setAuthorityRef(new AuthorityRef("admin", tenantRef));
        userContext.setDbSchema(schema);
        userContext.addRole(ROLE_ADMIN);
        userContext.addRole(ROLE_USER);
        userContext.addScope(SCOPE_DEFAULT);
        sessionContext.setUserContext(userContext);
        log.debug("Logged as admin {}", userContext);
        return userContext;
    }

    @Override
    public void loginAsGuest() {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setAuthorityRef(AuthorityRef.valueOf("guest"));
        userContext.setDbSchema(masterSchema);
        sessionContext.setUserContext(userContext);
        log.debug("Logged as guest {}", userContext);
    }

    @Override
    public Collection<PkItem> listPublicKeys() {
        var ctx = sessionContext.getUserContext();
        return securityDAO.listPublicKeys(ctx.getTenantRef(), ctx.getDbSchema());
    }

    @Override
    public PkItem addOrUpdatePublicKey(PkItem item) {
        if (item.getKid() == null || item.getKid().isEmpty()) {
            item.setKid(UuidCreator.getTimeOrderedEpoch().toString());
        }

        var normalizedKey = item.getKey()
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replaceAll("\r\n?|\n", "")
            .replace("-----END PUBLIC KEY-----", "");
        item.setKey(normalizedKey);

        var ctx = sessionContext.getUserContext();
        securityDAO.persistPublicKey(ctx.getTenantRef(), ctx.getDbSchema(), item);
        return item;
    }

    @Override
    public boolean deletePublicKey(String kid) {
        var ctx = sessionContext.getUserContext();
        return securityDAO.deletePublicKey(ctx.getTenantRef(), ctx.getDbSchema(), kid);
    }

    @Override
    public UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password) {
        return authenticateUser(authorityRef, password, null, SessionMode.SYNC);
    }

    @Override
    public UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password, SessionMode mode) {
        return authenticateUser(authorityRef, password, null, mode);
    }

    @Override
    public UserContext authenticateUser(AuthorityRef authorityRef, Optional<String> password, Set<String> additionalRoles, SessionMode mode) {
        final UserContext ctx;
        var pw = Optional.ofNullable(password).flatMap(x -> x).orElse(null);
        if (pw == null) {
            var cacheKey = "Internal " + authorityRef.toString();
            var cacheValue = contextCache.getIfPresent(cacheKey);
            if (cacheValue != null && !cacheValue.isExpired()) {
                var userContext = cacheValue.getObject().copy();
                log.debug("Found cached internal context for {} expiring at {}", userContext.getAuthorityRef(), cacheValue.getExpires());
                ctx = userContext;
            } else {
                ctx = authenticate(authorityRef, password, null, mode);
                contextCache.put(cacheKey, new Expirable<>(ctx, ZonedDateTime.now().plusSeconds(appExpiryTime.toSeconds())));
            }
        } else {
            var credentials = authorityRef.toString() + ":" + pw;
            var encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            ctx = authenticateWithBasicCredentials(encodedCredentials);
        }

        sessionContext.setMode(mode);
        // add additional roles
        if (ctx instanceof UserContextImpl ucxt) {
            var roles = new ArrayList<String>();
            if (sessionContext.getUserContext() != null) {
                roles.addAll(sessionContext.getUserContext().getRoleSet());
            }
            if (additionalRoles != null && !additionalRoles.isEmpty()) {
                roles.addAll(additionalRoles);
            }
            if (!roles.isEmpty()) {
                roles.stream().filter(Objects::nonNull).map(String::toLowerCase).distinct().forEach(ucxt::addRole);
            }
        }
        sessionContext.setUserContext(ctx);
        log.debug("Account {} logged in with roles {} (mode {})", ctx.getAuthorityRef(), ctx.getRoleSet(), mode);
        return ctx;
    }

    @Override
    public void authenticateUsingPK(TenantRef tenantRef, String pubKey, Collection<String> scopes) {
        TenantSpace t = tenantRepository.findByIdOptional(tenantRef.toString())
            .filter(x -> StringUtils.isNotBlank(x.getSchema()))
            .orElseThrow(UnauthorizedException::new);

        tenantRef = TenantRef.valueOf(t.getTenant());

        var authenticatedUser = securityDAO.findUserByPubKey(pubKey, tenantRef.toString(), t.getSchema())
            .filter(au -> BooleanUtils.isTrue(au.getUser().getData().getEnabled()))
            .filter(au -> !au.getUser().getData().isLocked())
            .orElseThrow(UnauthorizedException::new);

        login(t, authenticatedUser, scopes);
    }

    private UserContextImpl login(TenantSpace t, AuthenticatingUserRepresentation authenticatedUser, Collection<String> scopes) {
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
        if (Strings.CI.equals(user.getUsername(), "admin")) {
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

        sessionContext.setUserContext(userContext);
        log.debug("Security Context {} set with roles {}", userContext.getAuthorityRef(), userContext.getRoleSet());
        return userContext;
    }

    @Override
    public void authenticateUserOnBehalfOf(AuthorityRef authorityRef, UserContext currentUserContent) {
        Predicate<User> predicate = currentUserContent.isUserInRole(ROLE_SYSADMIN) || Strings.CI.equals(authorityRef.getTenantRef().toString(), temporaryUseCase.getTemporaryTenant())
            ? (User u) -> true
            : (User u) -> u.getData().getAuthorizedGuests().contains(currentUserContent.getAuthority());
        //noinspection OptionalAssignedToNull
        var ctx = authenticate(authorityRef, null, predicate, SessionMode.SYNC);
        if (currentUserContent.getRoleSet() != null) {
            currentUserContent.getRoleSet().forEach(ctx::addRole);
        }
    }

    @Override
    public TenantRef autenticateIfRequired(TenantRef tenantRef, boolean adminRequired) {
        var authorityRef = sessionContext.getUserContext().getAuthorityRef();
        var currentUserContext = sessionContext.getUserContext();
        if (Strings.CI.equals(authorityRef.getTenantRef().toString(), tenantRef.toString())
            && (!adminRequired || currentUserContext.isUserInRole(UserContext.ROLE_ADMIN))) {
            tenantRef = authorityRef.getTenantRef();
        } else if (currentUserContext.isUserInRole(UserContext.ROLE_SYSADMIN)) {
            log.debug("Login as admin having roles {}", currentUserContext.getRoleSet());
            //noinspection OptionalAssignedToNull
            var ctx = authenticateUser(new AuthorityRef("admin", tenantRef), null, currentUserContext.getRoleSet(), SessionMode.SYNC);
            tenantRef = ctx.getTenantRef();
        } else if (Strings.CI.equals(authorityRef.getTenantRef().toString(), tenantRef.toString())) {
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
                if (Strings.CS.equals(authorityRef.getIdentity(), "admin") || Strings.CI.equals(authorityRef.getIdentity(), alternativeRef.getIdentity())) {
                    if (Strings.CI.equals(alternativeRef.getTenantRef().toString(), authorityRef.getTenantRef().toString())) {
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

    private UserContextImpl authenticate(AuthorityRef authorityRef, Optional<String> password, Predicate<User> userPredicate, SessionMode mode) {
        log.debug("Authenticating user {} (mode {})", authorityRef, mode);
        var tenantName = authorityRef.getTenantRef().toString();
        var currentUserContext = sessionContext.getUserContext();
        boolean sysMode = Optional.ofNullable(currentUserContext).map(c -> c.isUserInRole(UserContext.ROLE_SYSADMIN)).orElse(false) || mode == SessionMode.ASYNC;
        TenantSpace t = tenantRepository.findByIdOptional(tenantName)
            .filter(x -> x.getData().isEnabled() || sysMode)
            .filter(x -> StringUtils.isNotBlank(x.getSchema()))
            .orElseThrow(() -> new UnauthorizedException("Tenant not found: " + tenantName));

        authorityRef = new AuthorityRef(authorityRef.getIdentity(), TenantRef.valueOf(t.getTenant()));

        log.debug("Looking for user {} in schema {}", authorityRef, t.getSchema());
        var identity = authorityRef.getIdentity();
        var user = securityDAO.findUser(authorityRef, t.getSchema())
            .filter(u -> BooleanUtils.isTrue(u.getData().getEnabled()))
            .filter(u -> !u.getData().isLocked() || sysMode)
            .filter(u ->
                Optional.ofNullable(password).isEmpty() ||
                match(
                    Optional.of(password).flatMap(x -> x).orElse(null),
                    u.getData().getPassword(),
                    Objects.requireNonNullElse(u.getData().getAlg(), defaultAlg)))
            .filter(u -> userPredicate == null || userPredicate.test(u))
            .orElse(null);

        if (user == null) {
            var tenant = Optional.ofNullable(currentUserContext).map(c -> c.getTenantRef().toString()).orElseThrow(IllegalStateException::new);
            if (Strings.CI.equals(identity, "admin")
                && Optional.ofNullable(password).isEmpty()
                && (
                mode == SessionMode.ASYNC
                    || currentUserContext.isUserInRole(UserContext.ROLE_SYSADMIN)
                    || Strings.CS.equals(tenantName, tenant))
            ) {
                var userContext = loginAsAdmin(authorityRef.getTenantRef(), t.getSchema());
                userContext.setDbSchema(t.getSchema());
                userContext.getAttributes().put(TENANT_DATA_ATTR, t.getData());

                sessionContext.setMode(mode);
                return userContext;
            }

            throw new UnauthorizedException();
        }

        var authenticatedUser = new AuthenticatingUserRepresentation();
        authenticatedUser.setUser(user);
        authenticatedUser.getScopes().add(SCOPE_DEFAULT);
        var ctx = login(t, authenticatedUser, Set.of());

        sessionContext.setMode(mode);
        return ctx;
    }

    @Override
    public UserContext authenticateWithToken(String token, String authority) {
        return authenticateWithToken(token, authority, ZonedDateTime.now(), SessionMode.SYNC);
    }

    @Override
    public UserContext authenticateWithToken(String token, String authority, ZonedDateTime time, SessionMode mode) {
        sessionContext.setToken(token);
        sessionContext.setMode(mode);

        var cacheKey = token + Optional.ofNullable(authority).map(a -> "-" + a).orElse("");
        var cacheValue = contextCache.getIfPresent(cacheKey);
        if (cacheValue != null && !cacheValue.wasExpiredAt(time)) {
            var userContext = (UserContextImpl) cacheValue.getObject().copy();
            this.sessionContext.setUserContext(userContext);
            log.debug("Account {} logged in with roles {} using cached JWT expiring at {} (mode {})", userContext.getAuthorityRef(), userContext.getRoleSet(), cacheValue.getExpires(), mode);
            return userContext;
        }

        DecodedJWT jwt = JWT.decode(token);
        if (jwt.getExpiresAtAsInstant() == null) {
            throw new UnauthorizedException("Unsupported token without expiration");
        } else if (jwt.getExpiresAtAsInstant().isBefore(Optional.ofNullable(time).map(ZonedDateTime::toInstant).orElse(Instant.now()))) {
            throw new UnauthorizedException("Token expired");
        } else if (jwt.getIssuedAtAsInstant() != null && jwt.getIssuedAtAsInstant().isAfter(Instant.now())) {
            throw new UnauthorizedException("Token not valid yet");
        }

        var subject = subject(jwt.getSubject(), authority);
        if (StringUtils.isBlank(subject)) {
            throw new UnauthorizedException("No authority specified");
        }

        final UserContextImpl result;
        if (sysKey.isPresent() && Strings.CS.equals(jwt.getKeyId(), sysKid)) {
            log.debug("Authenticating using system key");
            try {
                var normalizedKey = sysKey.get()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replaceAll("\r\n?|\n", "")
                    .replace("-----END PUBLIC KEY-----", "");

                try {
                    validateJWTAt(jwt, normalizedKey, time);
                } catch (RuntimeException e) {
                    log.warn("Validation exception {} using system key", e.getMessage());
                    throw e;
                }

                if (Strings.CS.equals(subject, "sysmon")) {
                    //noinspection OptionalAssignedToNull
                    var ctx = authenticate(AuthorityRef.valueOf("admin@default"), null, null, mode);
                    result = new UserContextImpl();
                    result.setAuthorityRef(ctx.getAuthorityRef());
                    result.setDbSchema(ctx.getDbSchema());
                    result.addRole(ROLE_SYSMON);
                    this.sessionContext.setUserContext(result);
                } else {
                    boolean sysadmin = Strings.CS.equals(subject, "sysadmin");
                    final UserContextImpl syscxt;
                    if (sysadmin) {
                        syscxt = new UserContextImpl();
                        syscxt.setAuthorityRef(AuthorityRef.valueOf("sysadmin"));
                        syscxt.setDbSchema(masterSchema);
                        syscxt.addRole(ROLE_SYSADMIN);
                        syscxt.addRole(ROLE_SYS);
                        this.sessionContext.setUserContext(syscxt);
                    } else {
                        syscxt = null;
                    }

                    if ((sysadmin || Strings.CS.equals(subject,"admin")) && jwt.getAudience() != null) {
                        subject = jwt.getAudience().stream().filter(StringUtils::isNotBlank).findFirst().orElse(subject);
                    }

                    if (syscxt != null && Strings.CS.equals(subject, "sysadmin")) {
                        result = syscxt;
                    } else {
                        var roleMap = Stream.of(ROLE_USER, ROLE_POWERUSER, ROLE_ADMIN, ROLE_SYSADMIN, ROLE_SYS, ROLE_POWERADMIN, ROLE_SYSMON)
                                .collect(Collectors.toMap(String::toLowerCase, Function.identity()));

                        //noinspection OptionalAssignedToNull
                        result = authenticate(AuthorityRef.valueOf(subject), null, null, mode);
                        result.addRole(ROLE_SYS);
                        Optional.ofNullable(jwt.getClaim("roles"))
                                .map(claim -> claim.asList(String.class))
                                .stream()
                                .flatMap(Collection::stream)
                                .map(String::toLowerCase)
                                .map(roleMap::get)
                                .filter(Objects::nonNull)
                                .forEach(result::addRole);

                        if (sysadmin) {
                            result.addRole(ROLE_SYSADMIN);
                        }

                        sessionContext.setUserIdentity(Optional.ofNullable(jwt.getClaim("identity")).map(Claim::asString).orElse(null));
                    }
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
            TenantSpace t = tenantRepository.findByIdOptional(tenantName)
                .filter(x -> StringUtils.isNotBlank(x.getSchema()))
                .orElseThrow(() -> new UnauthorizedException("Tenant not found: " + tenantName));

            var authorityRef = new AuthorityRef(requestedAuthorityRef.getIdentity(), TenantRef.valueOf(t.getTenant()));
            result = securityDAO
                .findUserByKid(jwt.getKeyId(), authorityRef, t.getSchema())
                .map(a -> {
                    try {
                        validateJWTAt(jwt, a.getPublicKey(), time);

                        var aud = jwt.getAudience();
                        if (aud != null && !aud.isEmpty()) {
                            var target = AuthorityRef.valueOf(aud.stream().findFirst().orElseThrow(ForbiddenException::new));
                            if (!Strings.CI.equals(authorityRef.toString(), target.toString())) {
                                if (!a.getScopes().contains(SCOPE_SYSADMIN)) {
                                    throw new ForbiddenException("Account not available for scope " + SCOPE_SYSADMIN);
                                }

                                log.debug("Got a sysadmin key. Authenticating on behalf of {}", target);
                                //noinspection OptionalAssignedToNull
                                var ctx = authenticate(target, null, null, mode);
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

        result.setAuthenticationScheme(SecurityContext.CLIENT_CERT_AUTH);

        sessionContext.setApplication(jwt.getIssuer());
        sessionContext.setUserIdentity(jwt.getClaim("preferred_username").asString());
        log.debug("Account {} logged in with roles {} using JWT", result.getAuthorityRef(), result.getRoleSet());
        contextCache.put(cacheKey, new Expirable<>(result, ZonedDateTime.ofInstant(jwt.getExpiresAtAsInstant(), ZoneId.systemDefault())));
        return result;
    }

    private void validateJWTAt(DecodedJWT jwt, String encodedKey, ZonedDateTime time) throws Exception {
        var algorithm = Algorithm.RSA256((RSAPublicKey) getPublicKey(encodedKey), null);
        long leewaySeconds = Duration.between(time, ZonedDateTime.now()).getSeconds();
        var verifier = JWT.require(algorithm).acceptLeeway(leewaySeconds).build();
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

    @Override
    public UserContext authenticateWithBasicCredentials(String encodedCredentials) {
        var cacheKey = "Basic " + encodedCredentials;
        var cacheValue = contextCache.getIfPresent(cacheKey);
        if (cacheValue != null && !cacheValue.isExpired()) {
            var userContext = cacheValue.getObject().copy();
            sessionContext.setUserContext(userContext);
            log.debug("Account {} logged in with roles {} using cached credentials expiring at {}", userContext.getAuthorityRef(), userContext.getRoleSet(), cacheValue.getExpires());
            return userContext;
        }

        var s = new String(Base64.getDecoder().decode(encodedCredentials)).split(":");
        if (s.length != 2) {
            throw new UnauthorizedException("Invalid credentials format: expected username:password");
        }

        var username = s[0];
        var password = s[1];

        var authorityRef = AuthorityRef.valueOf(username);
        var userContext = authenticate(authorityRef, Optional.ofNullable(password), null, SessionMode.SYNC);
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
            return Strings.CS.equals(expectedPassword, myHash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to hash password using ALG {}: {}", usingAlg, e.getMessage());
            return false;
        }
    }
}

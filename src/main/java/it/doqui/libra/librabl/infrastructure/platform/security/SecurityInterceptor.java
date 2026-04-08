package it.doqui.libra.librabl.infrastructure.platform.security;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.ErrorMessage;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

@Provider
@PreMatching
@Slf4j
public class SecurityInterceptor implements ContainerRequestFilter {

    @ConfigProperty(name = "libra.rest.basic-auth.username")
    Optional<String> username;

    @ConfigProperty(name = "libra.rest.basic-auth.password")
    Optional<String> password;

    @ConfigProperty(name = "libra.rest.basic-auth.apikey")
    Optional<String> apikey;

    @ConfigProperty(name = "libra.module.cxf.enabled", defaultValue = "true")
    boolean restV1Enabled;

    @Inject
    AuthenticationManager authenticationManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SessionContextHolder sessionContext;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        sessionContext.setUserContext(null);
        sessionContext.setMode(SessionMode.SYNC);

        String path = requestContext.getUriInfo().getPath();
        if (!restV1Enabled && Strings.CS.startsWith(requestContext.getUriInfo().getPath(), "/v1/")) {
            requestContext.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE).build());
            return;
        }

        String method = requestContext.getMethod();
        log.debug("Method: {} Path: {} Headers: {}", method, path, requestContext.getHeaders());

        if (Strings.CS.startsWith(path, "/v1/")) {
            v1Filter(requestContext);
        } else {
            v2Filter(requestContext);
        }
    }

    private void v2Filter(ContainerRequestContext requestContext) {
        sessionContext.setChannel(UserContext.CHANNEL_REST);
        sessionContext.setApiLevel(2);
        sessionContext.setMode(SessionMode.SYNC);

        var requestId = requestContext.getHeaderString("X-Request-Id");
        if (StringUtils.isNotBlank(requestId)) {
            sessionContext.setOperationId(requestId);
        }

        sessionContext.setApplication(requestContext.getHeaderString("X-Request-App"));

        var authorizationHeader = requestContext.getHeaderString("X-Authorization");
        if (!validateBasicAuth(requestContext) && authorizationHeader == null) {
            authorizationHeader = requestContext.getHeaderString("Authorization");
        }

        if (authorizationHeader != null) {
            String[] authorization = authorizationHeader.split(" ", 2);
            if (authorization.length > 1) {
                try {
                    if (Strings.CS.equals(authorization[0], "Bearer")) {
                        var userContext = authenticationManager.authenticateWithToken(authorization[1], requestContext.getHeaderString("X-Authority"));
                        requestContext.setSecurityContext(userContext);
                        sessionContext.setUserContext(userContext);
                    } else {
                        throw new UnauthorizedException("Invalid authentication method");
                    }
                } catch (WebException e) {
                    requestContext.abortWith(Response.status(e.getCode()).entity(new ErrorMessage(e.getCode(), e.getMessage())).build());
                } catch (Exception e) {
                    requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(new ErrorMessage(403, e.getMessage())).build());
                }
            }
        } else {
            authenticationManager.loginAsGuest();
        }
    }

    private void v1Filter(ContainerRequestContext requestContext) {
        try {
            sessionContext.setChannel(UserContext.CHANNEL_REST);
            sessionContext.setApiLevel(1);
            sessionContext.setMode(SessionMode.SYNC);

            if (!validateBasicAuth(requestContext)) {
                throw new UnauthorizedException("No authorization header");
            }

            var requestAuthHeader = requestContext.getHeaderString("X-Request-Auth");
            if (StringUtils.isNotBlank(requestAuthHeader)) {
                var s = new String(Base64.getDecoder().decode(StringUtils.stripToEmpty(requestAuthHeader)));
                if (StringUtils.isBlank(s) || !s.trim().startsWith("{")) {
                    throw new IllegalArgumentException("Invalid authentication header format");
                }

                var authContextHeader = objectMapper.readValue(s, AuthContextHeader.class);
                var userContext = authenticationManager.authenticateUser(
                    new AuthorityRef(authContextHeader.getUsername(), TenantRef.valueOf(authContextHeader.getTenant())),
                    Optional.of(authContextHeader.getPassword()));

                sessionContext.setApplication(authContextHeader.getApplication());
                sessionContext.setUserIdentity(authContextHeader.getUserIdentity());
                var requestId = requestContext.getHeaderString("X-Request-Id");
                if (StringUtils.isNotBlank(requestId)) {
                    sessionContext.setOperationId(requestId);
                }
                requestContext.setSecurityContext(userContext);
                sessionContext.setUserContext(userContext);
            } else {
                authenticationManager.loginAsGuest();
            }

        } catch (WebException e) {
            requestContext.abortWith(Response.status(e.getCode()).entity(new ErrorMessage(e.getCode(), e.getMessage())).build());
        } catch (Exception e) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(new ErrorMessage(403, e.getMessage())).build());
        }
    }

    private boolean validateBasicAuth(ContainerRequestContext requestContext) {
        if (username.isPresent()) {
            if (apikey.isPresent()) {
                var apikeyHeader = requestContext.getHeaderString("X-Api-Key");
                if (apikeyHeader != null) {
                    var s = new String(java.util.Base64.getDecoder().decode(apikeyHeader));
                    if (!Strings.CS.equals(s, apikey.orElse(""))) {
                        throw new UnauthorizedException("Invalid api-key header");
                    }

                    return false;
                }
            }

            var authorizationHeader = requestContext.getHeaderString("Authorization");
            if (StringUtils.isBlank(authorizationHeader)) {
                return Strings.CS.startsWith(requestContext.getUriInfo().getPath(), "/v1/tests");
            }

            String[] authorization = authorizationHeader.split(" ", 2);
            if (Strings.CS.equals(authorization[0], "Basic")) {
                var s = new String(java.util.Base64.getDecoder().decode(authorization[1])).split(":");
                if (!Strings.CS.equals(username.orElse(""), s[0]) || !Strings.CS.equals(password.orElse(""), s[1])) {
                    throw new UnauthorizedException("Invalid credentials");
                }

                return true;
            } else if (Strings.CS.equals(authorization[0], "Bearer")) {
                return false;
            } else {
                throw new UnauthorizedException("Invalid authorization header");
            }
        } else {
            return false;
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AuthContextHeader {
        private String username;
        private String password;
        private String tenant;

        @JsonProperty("application")
        @JsonAlias("fruitore")
        private String application;

        @JsonProperty("userIdentity")
        @JsonAlias("nomeFisico")
        private String userIdentity;
    }

}

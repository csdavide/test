package it.doqui.libra.librabl.business.provider.security;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.business.service.auth.UserContext;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.ErrorMessage;
import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.foundation.exceptions.UnauthorizedException;
import it.doqui.libra.librabl.foundation.exceptions.WebException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
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

    @Inject
    AuthenticationManager authenticationService;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        UserContextManager.removeContext();
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();
        log.debug("Method: {} Path: {} Headers: {}", method, path, requestContext.getHeaders());

        if (StringUtils.startsWith(path, "/v2/shares/download/")
            || StringUtils.startsWith(path, "/v2/shares/signed-download")
            || StringUtils.startsWith(path, "/v1/tests")
        ) {
            log.trace("No authentication required for path: {}", path);
        } else if (StringUtils.startsWith(path, "/v1/")) {
            v1Filter(requestContext);
        } else {
            v2Filter(requestContext);
        }
    }

    private void v2Filter(ContainerRequestContext requestContext) {
        var authorizationHeader = requestContext.getHeaderString("X-Authorization");
        if (!validateBasicAuth(requestContext) && authorizationHeader == null) {
            authorizationHeader = requestContext.getHeaderString("Authorization");
        }

        if (authorizationHeader != null) {
            String[] authorization = authorizationHeader.split(" ", 2);
            if (authorization.length > 1) {
                try {
                    final UserContextImpl userContext;
                    if (StringUtils.equals(authorization[0], "Basic")) {
                        userContext = authenticationService
                            .authenticateWithBasicCredentials(authorization[1]);
                    } else if (StringUtils.equals(authorization[0], "Bearer")) {
                        userContext = authenticationService
                            .authenticateWithToken(authorization[1], requestContext.getHeaderString("X-Authority"));
                    } else {
                        throw new UnauthorizedException("Invalid authentication method");
                    }

                    userContext.setChannel(UserContext.CHANNEL_REST);
                    userContext.setApiLevel(2);
                    var requestId = requestContext.getHeaderString("X-Request-Id");
                    if (StringUtils.isNotBlank(requestId)) {
                        userContext.setOperationId(requestId);
                    }

                    userContext.setApplication(requestContext.getHeaderString("X-Request-App"));
                    requestContext.setSecurityContext(userContext);
                } catch (WebException e) {
                    requestContext.abortWith(Response.status(e.getCode()).entity(new ErrorMessage(e.getCode(), e.getMessage())).build());
                }
            }
        }
    }

    private void v1Filter(ContainerRequestContext requestContext) throws IOException {
        try {
            validateBasicAuth(requestContext);
            var requestAuthHeader = requestContext.getHeaderString("X-Request-Auth");
            if (StringUtils.isNotBlank(requestAuthHeader)) {
                var s = new String(java.util.Base64.getDecoder().decode(StringUtils.stripToEmpty(requestAuthHeader)));
                var authContextHeader = objectMapper.readValue(s, AuthContextHeader.class);
                var userContext = authenticationService.authenticateUser(
                    new AuthorityRef(
                        authContextHeader.getUsername(),
                        TenantRef.valueOf(authContextHeader.getTenant())
                    ),
                    Optional.of(authContextHeader.getPassword()));

                userContext.setChannel(UserContext.CHANNEL_REST);
                userContext.setApiLevel(1);
                userContext.setApplication(authContextHeader.getApplication());
                userContext.setUserIdentity(authContextHeader.getUserIdentity());
                var requestId = requestContext.getHeaderString("X-Request-Id");
                if (StringUtils.isNotBlank(requestId)) {
                    userContext.setOperationId(requestId);
                }
                requestContext.setSecurityContext(userContext);
            }

        } catch (WebException e) {
            requestContext.abortWith(Response.status(e.getCode()).entity(new ErrorMessage(e.getCode(), e.getMessage())).build());
        }
    }

    private boolean validateBasicAuth(ContainerRequestContext requestContext) {
        if (username.isPresent()) {
            if (apikey.isPresent()) {
                var apikeyHeader = requestContext.getHeaderString("X-Api-Key");
                if (apikeyHeader != null) {
                    var s = new String(java.util.Base64.getDecoder().decode(apikeyHeader));
                    if (!StringUtils.equals(s, apikey.orElse(""))) {
                        throw new UnauthorizedException("Invalid api-key header");
                    }

                    return false;
                }
            }

            var authorizationHeader = requestContext.getHeaderString("Authorization");
            if (StringUtils.isBlank(authorizationHeader)) {
                if (StringUtils.startsWith(requestContext.getUriInfo().getPath(), "/v2/management/")) {
                    return true;
                }

                authorizationHeader = requestContext.getHeaderString("X-Authorization");
                if (StringUtils.startsWith(authorizationHeader, "Bearer ")) {
                    return true;
                }

                throw new UnauthorizedException("No authorization header");
            }

            String[] authorization = authorizationHeader.split(" ", 2);
            if (StringUtils.equals(authorization[0], "Basic")) {
                var s = new String(java.util.Base64.getDecoder().decode(authorization[1])).split(":");
                if (!StringUtils.equals(username.orElse(""), s[0]) || !StringUtils.equals(password.orElse(""), s[1])) {
                    throw new UnauthorizedException("Invalid credentials");
                }

                return true;
            } else if (StringUtils.equals(authorization[0], "Bearer")) {
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

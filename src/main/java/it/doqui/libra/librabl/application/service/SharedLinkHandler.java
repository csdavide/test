package it.doqui.libra.librabl.application.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import it.doqui.libra.librabl.domain.model.graph.NodeDescriptor;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import it.doqui.libra.librabl.application.model.share.SharingItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static it.doqui.libra.librabl.domain.model.graph.Constants.PROP_ECMSYS_SHARED_LINKS;

@ApplicationScoped
public class SharedLinkHandler {

    @ConfigProperty(name = "libra.files.hmac.secret")
    String secret;

    @ConfigProperty(name = "libra.files.hmac.issuer", defaultValue = "https://doqui.it/libra")
    String issuer;

    @ConfigProperty(name = "libra.files.hmac.duration", defaultValue = "PT60S")
    Duration duration;

    @Inject
    SessionContext sessionContext;

    private final SecureRandom random = new SecureRandom();

    public Collection<SharingItem> listSharingItems(NodeDescriptor nd) {
        if (nd.getProperties().get(PROP_ECMSYS_SHARED_LINKS) instanceof Collection<?> sharedLinks) {
            return sharedLinks.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(this::asSharingItem)
                    .filter(Objects::nonNull)
                    .filter(item -> StringUtils.isNotBlank(item.getUrl()))
                    .toList();
        }

        return List.of();
    }

    public String generatePublicLink(String uuid, String kind, String contentPropertyName) {
        var key = createEncodedKey(uuid, kind, contentPropertyName);
        return ConfigProvider.getConfig()
                .getOptionalValue("sharedlink.internet", String.class)
                .map(prefix -> Strings.CS.endsWith(prefix, "/") ? prefix : prefix + "/")
                .map(prefix -> prefix + key)
                .orElse(key);
    }

    public String generateSignedLink(String uuid, String contentUrl) {
        var alg = Algorithm.HMAC256(secret);
        var tk = JWT.create()
                .withIssuer(issuer)
                .withSubject(sessionContext.getUserContext().getAuthority())
                .withClaim("uuid", uuid)
                .withClaim("contentUrl", contentUrl)
                .withIssuedAt(Instant.now().minusSeconds(600))
                .withExpiresAt(Instant.now().plusSeconds(duration.toSeconds()))
                .sign(alg);
        return ConfigProvider.getConfig()
                .getOptionalValue("sharedlink.internet", String.class)
                .map(prefix -> Strings.CS.endsWith(prefix, "/") ? prefix : prefix + "/")
                .map(prefix -> prefix + tk)
                .orElse(tk);
    }

    public String createEncodedKey(String uuid, String kind, String contentPropertyName) {
        var key = String.join("|", List.of(
                kind,
                sessionContext.getUserContext().getTenantRef().toString(),
                uuid.replace("-", ""),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(random.nextInt(10000000))));
        if (contentPropertyName != null) {
            key += "|" + contentPropertyName;
        }

        return encodeBase64(key);
    }

    public SharingItem asSharingItem(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }

        var a = s.split("\\|");
        var key = a[0];
        var item = new SharingItem();
        item.setKey(key);
        item.setSource(StringUtils.stripToNull(ArrayUtils.get(a, 1)));
        item.setContentPropertyName(ArrayUtils.get(a, 2));
        item.setFilePropertyName(StringUtils.stripToNull(ArrayUtils.get(a, 3)));
        item.setDisposition(StringUtils.stripToNull(ArrayUtils.get(a, 4)));
        item.setFromDate(Optional.ofNullable(ArrayUtils.get(a, 5)).map(StringUtils::stripToNull).map(DateISO8601Utils::parseAsZonedDateTime).orElse(null));
        item.setToDate(Optional.ofNullable(ArrayUtils.get(a, 6)).map(StringUtils::stripToNull).map(DateISO8601Utils::parseAsZonedDateTime).orElse(null));
        item.setUrl(
                ConfigProvider.getConfig()
                        .getOptionalValue("sharedlink." + StringUtils.stripToEmpty(item.getSource()), String.class)
                        .map(prefix -> UriBuilder.fromUri(prefix).path(key).build().toString())
                        .orElse(null)
        );

        return item;
    }

    private String encodeBase64(String key) {
        return Base64.getUrlEncoder()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8))
                .replace("+", "-")
                .replace("/", "_")
                .replace("=", "");
    }
}

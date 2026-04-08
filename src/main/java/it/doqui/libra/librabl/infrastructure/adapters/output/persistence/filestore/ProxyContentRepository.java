package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.filestore;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.libra.librabl.domain.ports.out.ContentRepository;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@IfBuildProperty(name = "proxy.store.enabled", stringValue = "true")
@Slf4j
public class ProxyContentRepository extends DefaultContentRepository implements ContentRepository {

    @ConfigProperty(name = "proxy.cache.ttl", defaultValue = "24h")
    Duration cacheTTL;

    @ConfigProperty(name = "proxy.store.url")
    String remoteStoreUrl;

    @ConfigProperty(name = "libra.files.shared-sec")
    Optional<String> sharedSec;

    @ConfigProperty(name = "proxy.store.issuer", defaultValue = "https://doqui.it/index")
    String issuer;

    @ConfigProperty(name = "proxy.store.kid", defaultValue = "master")
    String kid;

    @ConfigProperty(name = "proxy.store.subject")
    Optional<String> subject;

    @ConfigProperty(name = "proxy.store.privateKey.path", defaultValue = "./privateKey.pem")
    String privateKeyPath;

    @ConfigProperty(name = "proxy.store.pool.max-total", defaultValue = "20")
    int maxTotal;

    @ConfigProperty(name = "proxy.store.pool.max-per-route", defaultValue = "10")
    int maxPerRoute;

    @ConfigProperty(name = "proxy.store.pool.connection-timeout", defaultValue = "5s")
    Duration connectionTimeout;

    @Inject
    ObjectMapper objectMapper;

    private Cache<String,Path> cache;

    private final CloseableHttpClient httpClient;

    public ProxyContentRepository() {
        PoolingHttpClientConnectionManager poolingConnManager = new PoolingHttpClientConnectionManager();
        poolingConnManager.setMaxTotal(maxTotal);
        poolingConnManager.setDefaultMaxPerRoute(maxPerRoute);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout((int) connectionTimeout.toMillis())
                .setSocketTimeout((int) connectionTimeout.toMillis())
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(poolingConnManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionTimeToLive(connectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(cacheTTL).recordStats().build();
    }

    private void fillSecurityHeaders(HttpRequestBase req) {
        sharedSec.ifPresent(s -> req.setHeader("X-Shared-Sec", s));

        try {
            var algorithm = Algorithm.RSA256(null, (RSAPrivateKey) getPrivateKey(Path.of(privateKeyPath)));
            var token = JWT.create()
                .withIssuer(issuer)
                .withKeyId(kid)
                .withIssuedAt(Instant.now().minus(Duration.of(120, ChronoUnit.MINUTES)))
                .withExpiresAt(Instant.now().plus(Duration.of(120, ChronoUnit.MINUTES)))
                .withSubject(subject.orElse(sessionContext.getUserContext().getAuthorityRef().toString()))
                .sign(algorithm);

            log.debug("Sending token {}", token);
            req.setHeader("X-Authorization", "Bearer " + token);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PrivateKey getPrivateKey(Path keyPath) throws Exception {
        var sb = new StringBuilder();
        try (var reader = Files.newBufferedReader(keyPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("---")) {
                    sb.append(line);
                }
            }
        }

        byte[] byteKey = Base64.getDecoder().decode(sb.toString().getBytes(StandardCharsets.UTF_8));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(byteKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    @Override
    public Path getPath(String contentUrl) throws IOException {
        try {
            URI uri = new URI(contentUrl);
            if (Strings.CS.equals(uri.getScheme(), "local")) {
                return super.getPath(contentUrl);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        var path = cache.getIfPresent(contentUrl);
        if (path != null) {
            log.debug("Found cached temp path {}", path);
            return path;
        }

        log.info("Retrieving url {}", contentUrl);
        try {
            URI uri = new URIBuilder(remoteStoreUrl)
                .addParameter("contentUrl", contentUrl)
                .build();
            HttpGet req = new HttpGet(uri);
            fillSecurityHeaders(req);

            try (CloseableHttpResponse response = httpClient.execute(req)) {
                try {
                    int statusCode = response.getStatusLine().getStatusCode();
                    log.debug("Remote Content Store returned: {}", statusCode);
                    if (statusCode != 200) {
                        throw new RuntimeException(String.format("Remote Content Store returned %d http error", statusCode));
                    }

                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        throw new RuntimeException("Remote Content Store returned an empty response");
                    }

                    FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw----"));
                    Path tempPath = Files.createTempFile("ecm-", null, attr);
                    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(tempPath))) {
                        int b;
                        while ((b = entity.getContent().read()) != -1) {
                            out.write(b);
                        }
                        out.flush();
                    }

                    tempPath.toFile().deleteOnExit();
                    log.info("Created temporary file {}", tempPath);
                    cache.put(contentUrl, tempPath);
                    return tempPath;
                } finally {
                    EntityUtils.consume(response.getEntity());
                }
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(String contentUrl) throws IOException {
        try {
            URI uri = new URI(contentUrl);
            String path = contentStoreMap.get(uri.getScheme());
            if (StringUtils.isNotBlank(path) && Strings.CS.equals(uri.getScheme(), "local")) {
                super.delete(contentUrl);
                return;
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        log.info("Deleting url {}", contentUrl);
        try {
            var req = new HttpDelete(new URIBuilder(remoteStoreUrl + "/" + URLEncoder.encode(contentUrl, StandardCharsets.UTF_8)).build());
            fillSecurityHeaders(req);

            try (var res = httpClient.execute(req)) {
                int status = res.getStatusLine().getStatusCode();
                switch (status) {
                    case 200, 204: return;
                    case 404: throw new FileNotFoundException("Got 404");
                    case 403: throw new IOException("Got 403");
                    default: throw new SystemException("Got " + status);
                }
            }
        } catch (URISyntaxException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public long writeStream(String contentUrl, InputStream stream) throws IOException {
        try {
            URI uri = new URI(contentUrl);
            String path = contentStoreMap.get(uri.getScheme());
            if (StringUtils.isNotBlank(path) && Strings.CS.equals(uri.getScheme(), "local")) {
                return super.writeStream(contentUrl, stream);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        log.info("Writing to url {} using {}", contentUrl, sessionContext.getUserContext().getAuthorityRef());
        try {
            URI uri = new URIBuilder(remoteStoreUrl + "/" + URLEncoder.encode(contentUrl, StandardCharsets.UTF_8)).build();
            HttpPut req = new HttpPut(uri);
            req.setHeader("Content-type", MediaType.APPLICATION_OCTET_STREAM);
            fillSecurityHeaders(req);

            req.setEntity(new InputStreamEntity(stream));

            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            };

            var responseBody = httpClient.execute(req, responseHandler);
            var responseObj = objectMapper.readValue(responseBody, WriteResponse.class);
            return responseObj.getSize();

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Getter
    @Setter
    private static class WriteResponse {
        private long size;
    }
}

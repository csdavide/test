package it.doqui.libra.librabl.infrastructure.adapters.output.persistence.filestore;

import io.quarkus.arc.DefaultBean;
import it.doqui.libra.librabl.domain.ports.out.ContentRepository;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@DefaultBean
@Slf4j
public class DefaultContentRepository implements ContentRepository {

    private static final int BUFFER_SIZE = 8192;

    @ConfigProperty(name = "libra.content-store.stores")
    Map<String,String> contentStoreMap;

    @Inject
    SessionContext sessionContext;

    private String getStorePath(TenantData d, String name) {
        String value = null;
        if (d != null) {
            value = d.getStores().get(name);
        }

        if (value == null) {
            value = contentStoreMap.get(name);
        }

        if (Strings.CS.startsWith(value, "$")) {
            return getStorePath(d, value.substring(1));
        }

        return value;
    }

    protected Path toPath(String contentUrl) {
        var parts = contentUrl.split(":/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid content url " + contentUrl);
        }

        var scheme = parts[0];
        var relativePath = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);

        var path = getStorePath(sessionContext.getTenantData().orElse(null), scheme);
        if (StringUtils.isBlank(path)) {
            throw new RuntimeException("Invalid content store in url " + contentUrl);
        }

        return Paths.get(path, relativePath);
    }

    @Override
    public Path getStorePath(String contentUrl) {
        try {
            var scheme = contentUrl.contains(":") ? new URI(contentUrl).getScheme() : contentUrl;
            var path = getStorePath(sessionContext.getTenantData().orElse(null), scheme);
            if (StringUtils.isBlank(path)) {
                return null;
            }

            return Paths.get(path);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> getStoresOfPath(Path path) {
        var schemes = new LinkedHashSet<String>();
        sessionContext.getTenantData().map(TenantData::getStores).ifPresent(stores -> {
            for (var entry : stores.entrySet()) {
                if (path.compareTo(Paths.get(entry.getValue())) == 0) {
                    schemes.add(entry.getKey());
                }
            }
        });

        if (schemes.isEmpty()) {
            for (var entry : contentStoreMap.entrySet()) {
                if (path.compareTo(Paths.get(entry.getValue())) == 0) {
                    schemes.add(entry.getKey());
                }
            }
        }

        return schemes;
    }

    @Override
    public void delete(String contentUrl) throws IOException {
        var p = toPath(contentUrl);
        if (!Files.exists(p)) {
            throw new FileNotFoundException("Unable to locate file " + p);
        }

        Files.delete(p);
    }

    @Override
    public Path getPath(String contentUrl) throws IOException {
        var p = toPath(contentUrl);
        if (!Files.exists(p)) {
            throw new IOException("Unable to locate file " + p);
        }

        return p;
    }

    @Override
    public long writeStream(String contentUrl, InputStream stream) throws IOException {
        var p = toPath(contentUrl);
        Files.createDirectories(p.getParent());
        try (var os = Files.newOutputStream(p)) {
            byte[] b = new byte[BUFFER_SIZE];
            long size = 0;
            int zeroCount = 0;
            long tLost = 0;
            long t0 = System.currentTimeMillis();
            long tCycle = t0;
            for (int bytesRead; (bytesRead = stream.read(b)) != -1;) {
                if (bytesRead > 0) {
                    os.write(b, 0, bytesRead);
                    size += bytesRead;
                } else {
                    tLost += System.currentTimeMillis() - tCycle;
                    zeroCount++;
                }

                tCycle = System.currentTimeMillis();
            }

            os.flush();
            if (zeroCount > 0) {
                log.warn("{} cycles of zero bytes performed and {} ms lost in {} ms", zeroCount, tLost, (System.currentTimeMillis() - t0));
            }
            return size;
        }
    }

}

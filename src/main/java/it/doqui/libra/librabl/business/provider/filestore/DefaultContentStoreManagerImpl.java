package it.doqui.libra.librabl.business.provider.filestore;

import io.quarkus.arc.DefaultBean;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.ContentStoreService;
import it.doqui.libra.librabl.views.tenant.TenantData;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@ApplicationScoped
@DefaultBean
@Slf4j
public class DefaultContentStoreManagerImpl implements ContentStoreService {

    private static final int BUFFER_SIZE = 4096;

    @ConfigProperty(name = "libra.content-store.stores")
    Map<String,String> contentStoreMap;

    private String getStorePath(TenantData d, String name) {
        String value = null;
        if (d != null) {
            value = d.getStores().get(name);
        }

        if (value == null) {
            value = contentStoreMap.get(name);
        }

        if (StringUtils.startsWith(value, "$")) {
            return getStorePath(d, value.substring(1));
        }

        return value;
    }

    protected Path toPath(String contentUrl) {
        try {
            var uri = new URI(contentUrl);
            var path = getStorePath(UserContextManager.getTenantData().orElse(null), uri.getScheme());
            if (StringUtils.isBlank(path)) {
                throw new RuntimeException("Invalid content store in url " + uri);
            }

            return Paths.get(String.format("%s/%s%s", path, uri.getAuthority(),uri.getPath()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path getStorePath(String contentUrl) {
        try {
            var scheme = contentUrl.contains(":") ? new URI(contentUrl).getScheme() : contentUrl;
            var path = getStorePath(UserContextManager.getTenantData().orElse(null), scheme);
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
        UserContextManager.getTenantData().map(TenantData::getStores).ifPresent(stores -> {
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
            for (int bytesRead; (bytesRead = stream.read(b)) != -1;) {
                os.write(b, 0, bytesRead);
                size += bytesRead;
            }

            os.flush();
            return size;
        }
    }

}

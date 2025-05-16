package it.doqui.libra.librabl.business.provider.engine;

import it.doqui.libra.librabl.business.provider.data.entities.ActiveNode;
import it.doqui.libra.librabl.business.provider.data.entities.NodeData;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import it.doqui.libra.librabl.business.service.interfaces.ContentStoreService;
import it.doqui.libra.librabl.business.service.interfaces.MimeTypeService;
import it.doqui.libra.librabl.business.service.node.NodeAttachment;
import it.doqui.libra.librabl.foundation.PrefixedQName;
import it.doqui.libra.librabl.foundation.exceptions.BadDataException;
import it.doqui.libra.librabl.foundation.exceptions.PreconditionFailedException;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import it.doqui.libra.librabl.utils.ObjectUtils;
import it.doqui.libra.librabl.views.node.ContentProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static it.doqui.libra.librabl.business.service.interfaces.Constants.ASPECT_ECMSYS_ENCRYPTED;
import static it.doqui.libra.librabl.business.service.interfaces.Constants.CM_NAME;

@ApplicationScoped
@Slf4j
public class ContentRetriever {

    @Inject
    ContentStoreService contentStoreService;

    @Inject
    MimeTypeService mimeTypeService;

    public String relocate(String contentUrl, Path path) {
        try {
            var stores = contentStoreService.getStoresOfPath(path);
            if (stores.isEmpty()) {
                throw new BadDataException(String.format("Unable to relocate content '%s' into current tenant", contentUrl));
            }

            URI uri = new URI(contentUrl);
            var scheme = uri.getScheme();
            if (!stores.contains(scheme)) {
                return contentUrl.replace(scheme, stores.stream().findFirst().orElse(scheme));
            }

            return contentUrl;
        } catch (URISyntaxException e) {
            throw new SystemException(e);
        }
    }

    public NodeAttachment retrieveContent(NodeData data, String contentPropertyName, String fileName) throws IOException {
        var cp = Optional.ofNullable(data.getContentProperty(contentPropertyName, fileName))
            .orElseThrow(PreconditionFailedException::new);

        var f = Optional.ofNullable(contentStoreService.getPath(cp.getContentUrl())).map(Path::toFile).orElse(null);
        var ctx = UserContextManager.getContext();
        var store = NodeAttachment.StoreLocation.builder()
            .dbSchema(ctx.getDbSchema())
            .tenant(ctx.getTenantRef().toString())
            .path(contentStoreService.getStorePath(cp.getContentUrl()))
            .build();

        return NodeAttachment.builder()
            .name(fileName(data, cp))
            .contentProperty(cp)
			.opaque(data.getAspects().contains(ASPECT_ECMSYS_ENCRYPTED) || cp.isOpaque())
            .file(f)
            .store(store)
            .build();

    }

    private String fileName(NodeData data, ContentProperty cp) {
        var fileName = cp.getFileName();
        if (fileName == null) {
            fileName = ObjectUtils.getAsString(data.getProperties().get(CM_NAME));
        }

        return fileName(fileName, cp.getMimetype(), Set.of());
    }

    public String fileName(ActiveNode n, ContentProperty cp, Set<String> nameSet) {
        var fileName = cp.getFileName();
        if (fileName == null) {
            fileName = ObjectUtils.getAsString(n.getData().getProperties().get(CM_NAME));
            if (fileName == null) {
                fileName = n.getParents().stream()
                    .filter(Objects::nonNull)
                    .map(p -> PrefixedQName.valueOf(p.getName()).getLocalPart())
                    .findFirst()
                    .orElse(null);
            }
        }

        return fileName(fileName, cp.getMimetype(), nameSet);
    }

    private String fileName(String fileName, String mimeType, Set<String> nameSet) {
        if (fileName != null) {
            var prefix = fileName;
            var lastDot = fileName.lastIndexOf('.');
            final String ext;
            if (lastDot > 0) {
                prefix = fileName.substring(0, lastDot);
                ext = fileName.substring(lastDot).toLowerCase();
            } else {
                ext = "";
            }

            String suffix;
            if (StringUtils.isNotBlank(ext)) {
                suffix = ext;
            } else if (mimeType != null) {
                suffix = mimeTypeService.getFileExtension(mimeType, false).map(x -> "." + x).orElse("");
            } else {
                suffix = "";
            }

            if (StringUtils.isBlank(suffix)) {
                suffix = ext;
            } else if (!StringUtils.equalsIgnoreCase(ext, suffix)) {
                prefix += ext;
            }

            fileName = prefix + suffix;
            var count = 0;
            while (nameSet.contains(fileName)) {
                count++;
                fileName = prefix + "_" + count + suffix;
            }
        }
        return fileName;
    }
}

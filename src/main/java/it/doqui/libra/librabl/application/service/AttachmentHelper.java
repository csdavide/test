package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.mappers.NodeMapper;
import it.doqui.libra.librabl.domain.model.files.FileMetadata;
import it.doqui.libra.librabl.domain.model.files.NodeAttachment;
import it.doqui.libra.librabl.domain.model.graph.PropertyProvider;
import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.ports.out.ContentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static it.doqui.libra.librabl.domain.model.graph.Constants.ASPECT_ECMSYS_ENCRYPTED;

@ApplicationScoped
@Slf4j
class AttachmentHelper {

    @Inject
    ContentRepository contentRepository;

    @Inject
    NodeMapper nodeMapper;

    @Inject
    SessionContext sessionContext;

    NodeAttachment attachment(PropertyProvider pp, FileMetadata fm) throws IOException {
        var cp = nodeMapper.toContentProperty(fm, pp);
        var f = Optional.ofNullable(contentRepository.getPath(cp.getContentUrl())).map(Path::toFile).orElse(null);
        var ctx = sessionContext.getUserContext();
        var store = NodeAttachment.StoreLocation.builder()
            .dbSchema(ctx.getDbSchema())
            .tenant(ctx.getTenantRef().toString())
            .path(contentRepository.getStorePath(cp.getContentUrl()))
            .build();

        return NodeAttachment.builder()
            .name(cp.getFileName())
            .contentProperty(cp)
            .opaque(pp.hasAspect(ASPECT_ECMSYS_ENCRYPTED) || cp.isOpaque())
            .file(f)
            .store(store)
            .build();
    }
}

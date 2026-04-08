package it.doqui.libra.librabl.domain.model.files;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface FileProvider {
    List<? extends FileMetadata> getContents();
    Optional<FileMetadata> getContent(URI contentUrl);
    Optional<FileMetadata> getContent(ContentReferenceable ref);
}

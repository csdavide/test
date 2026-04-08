package it.doqui.libra.librabl.domain.ports.out;

import it.doqui.libra.librabl.domain.model.mimetypes.MimeTypeMapping;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MimeTypeRepository {
    Optional<MimeTypeMapping> getById(long id);
    void deleteById(long id);
    void delete(Collection<Long> ids);
    void addAll(Collection<MimeTypeMapping> items);
    void replaceAll(Collection<MimeTypeMapping> items);
    Optional<String> getFileExtension(String mimeType, boolean includeStarExtensions);
    List<String> getAllFileExtensions(String mimeType, boolean includeStarExtensions);
    List<String> getAllMimeTypes(String fileExtension);
    List<MimeTypeMapping> list(MimeTypeMapping criteria, boolean includeStarExtensions);
}

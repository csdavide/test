package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.mimetypes.MimeTypeMapping;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MimeTypeUseCase {
    Optional<MimeTypeMapping> getById(long id);
    List<MimeTypeMapping> list(MimeTypeMapping criteria, boolean includeStarExtensions);
    void addAll(Collection<MimeTypeMapping> items);
    void replaceAll(Collection<MimeTypeMapping> items);
    void deleteById(long id);
    void delete(Collection<Long> ids);
}

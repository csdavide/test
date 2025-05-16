package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.views.mimetype.MimeTypeItem;
import it.doqui.libra.librabl.views.mimetype.MimeTypeRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MimeTypeService {
    void deleteById(long id);
    void delete(Collection<Long> ids);
    void addAll(Collection<MimeTypeRequest> items);
    void replaceAll(Collection<MimeTypeRequest> items);
    Optional<String> getFileExtension(String mimeType, boolean includeStarExtensions);
    List<String> getAllFileExtensions(String mimeType, boolean includeStarExtensions);
    Set<String> getAllMimeTypes(String fileExtension);
    List<MimeTypeItem> list(MimeTypeItem criteria, boolean includeStarExtensions);
    Optional<MimeTypeItem> getById(long id);
}

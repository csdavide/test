package it.doqui.libra.librabl.application.service;

import it.doqui.libra.librabl.application.ports.in.MimeTypeUseCase;
import it.doqui.libra.librabl.domain.model.mimetypes.MimeTypeMapping;
import it.doqui.libra.librabl.domain.ports.out.MimeTypeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class MimeTypeApplicationService implements MimeTypeUseCase {

    @Inject
    MimeTypeRepository mimeTypeRepository;

    @Override
    public Optional<MimeTypeMapping> getById(long id) {
        return mimeTypeRepository.getById(id);
    }

    @Override
    public List<MimeTypeMapping> list(MimeTypeMapping criteria, boolean includeStarExtensions) {
        return mimeTypeRepository.list(criteria, includeStarExtensions);
    }

    @Override
    public void addAll(Collection<MimeTypeMapping> items) {
        mimeTypeRepository.addAll(items);
    }

    @Override
    public void replaceAll(Collection<MimeTypeMapping> items) {
        mimeTypeRepository.replaceAll(items);
    }

    @Override
    public void deleteById(long id) {
        mimeTypeRepository.deleteById(id);
    }

    @Override
    public void delete(Collection<Long> ids) {
        mimeTypeRepository.delete(ids);
    }
}

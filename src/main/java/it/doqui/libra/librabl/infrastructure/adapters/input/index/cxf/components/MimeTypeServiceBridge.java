package it.doqui.libra.librabl.infrastructure.adapters.input.index.cxf.components;

import io.quarkus.arc.properties.IfBuildProperty;
import it.doqui.index.ecmengine.mtom.dto.Mimetype;
import it.doqui.index.ecmengine.mtom.exception.InvalidParameterException;
import it.doqui.libra.librabl.application.ports.in.MimeTypeUseCase;
import it.doqui.libra.librabl.domain.model.mimetypes.MimeTypeMapping;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.ModelMapper;

@IfBuildProperty(name = "libra.module.cxf.enabled", stringValue = "true", enableIfMissing = true)
@ApplicationScoped
@Slf4j
public class MimeTypeServiceBridge extends AbstractServiceBridge {

    @Inject
    MimeTypeUseCase mimeTypeUseCase;

    @Inject
    ModelMapper modelMapper;

    @Traceable(traceAllParameters = true, category = TraceCategory.READ)
    public Mimetype[] getMimetype(Mimetype mimetype) throws InvalidParameterException {
        boolean x = StringUtils.isBlank(mimetype.getFileExtension());
        boolean y = StringUtils.isBlank(mimetype.getMimetype());
        if ((x && y) || (!x && !y)) {
            throw new InvalidParameterException(
                String.format("EXTENSION: %s - MIMETYPE: %s - ONLY ONE PARAMETER MUST BE NOT NULL",
                    mimetype.getFileExtension(), mimetype.getMimetype()));
        }

        return mimeTypeUseCase
            .list(modelMapper.map(mimetype, MimeTypeMapping.class), true)
            .stream()
            .map(m -> modelMapper.map(m, Mimetype.class))
            .toList()
            .toArray(new Mimetype[0]);
    }
}

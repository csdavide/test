package it.doqui.libra.librabl.api.v1.cxf.impl;

import it.doqui.index.ecmengine.mtom.dto.Mimetype;
import it.doqui.index.ecmengine.mtom.exception.InvalidParameterException;
import it.doqui.libra.librabl.business.service.interfaces.MimeTypeService;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.views.mimetype.MimeTypeItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.ModelMapper;

@ApplicationScoped
@Slf4j
public class MimeTypeServiceBridge extends AbstractServiceBridge {

    @Inject
    MimeTypeService mimeTypeService;

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

        return mimeTypeService
            .list(modelMapper.map(mimetype, MimeTypeItem.class), true)
            .stream()
            .map(m -> modelMapper.map(m, Mimetype.class))
            .toList()
            .toArray(new Mimetype[0]);
    }
}

package it.doqui.libra.librabl.api.v1.rest.components.impl;

import it.doqui.index.ecmengine.mtom.dto.Document;
import it.doqui.index.ecmengine.mtom.dto.DocumentOperation;
import it.doqui.libra.librabl.api.v1.rest.dto.FileFormatInfo;
import jakarta.inject.Inject;
import org.apache.commons.lang3.BooleanUtils;

public abstract class AbstractBusinessComponent {

    @Inject
    protected DtoMapper dtoMapper;

    protected FileFormatInfo[] map(it.doqui.index.ecmengine.mtom.dto.FileFormatInfo[] infos) {
        var result = new FileFormatInfo[infos.length];
        for (int i = 0; i < infos.length; i++) {
            result[i] = dtoMapper.convert(infos[i], FileFormatInfo.class);
        }

        return result;
    }

    protected Document document(byte[] bytes, Boolean store) {
        return document(bytes, null, null, store);
    }

    protected Document document(byte[] bytes, String uuid, String contentPropertyName, Boolean store) {
        var document = new Document();
        document.setBuffer(bytes);
        document.setUid(uuid);
        document.setContentPropertyPrefixedName(contentPropertyName);
        var operation = new DocumentOperation();
        operation.setReturnData(false);
        operation.setTempStore(BooleanUtils.toBoolean(store));
        document.setOperation(operation);
        return document;
    }

    protected Document documentIfAny(byte[] bytes, String uuid, String contentPropertyName, Boolean store) {
        if (bytes != null || contentPropertyName != null || uuid != null || store != null) {
            return document(bytes, uuid, contentPropertyName, store);
        }

        return null;
    }
}

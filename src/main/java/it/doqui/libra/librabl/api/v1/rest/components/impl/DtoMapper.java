package it.doqui.libra.librabl.api.v1.rest.components.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DtoMapper {

    private final ObjectMapper objectMapper;

    public DtoMapper() {
        this.objectMapper = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .findAndRegisterModules();
    }

    public <T> T convert(Object obj, Class<T> targetClass) {
        try {
            var json = objectMapper.writeValueAsString(obj);
            return objectMapper.readValue(json, targetClass);
        } catch (JsonProcessingException e) {
            throw new SystemException(e);
        }
    }
}

package it.doqui.libra.librabl.infrastructure.adapters.input.rest.providers;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Duration;

@Provider
public class DurationConverterProvider implements ParamConverterProvider {
    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType.equals(Duration.class)) {
            return (ParamConverter<T>) new DurationParamConverter();
        }
        return null;
    }
}
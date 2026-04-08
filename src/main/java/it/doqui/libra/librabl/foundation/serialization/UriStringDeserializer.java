package it.doqui.libra.librabl.foundation.serialization;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import it.doqui.libra.librabl.utils.ObjectUtils;

import java.io.IOException;

public class UriStringDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        String value = jsonParser.getValueAsString();
        if (value == null) {
            return null;
        }

        return ObjectUtils.encodeUri(value);
    }
}
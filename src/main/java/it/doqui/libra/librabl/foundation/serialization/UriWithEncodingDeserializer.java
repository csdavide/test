package it.doqui.libra.librabl.foundation.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import it.doqui.libra.librabl.utils.ObjectUtils;

import java.io.IOException;
import java.net.URI;

public class UriWithEncodingDeserializer extends JsonDeserializer<URI> {

    @Override
    public URI deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        var uriString = jsonParser.getText();
        return URI.create(ObjectUtils.encodeUri(uriString));
    }
}

package it.doqui.libra.librabl.views.document;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Map;


public class OpaqueSerializer extends StdSerializer<byte[]> {

    public OpaqueSerializer() {
        this(null);
    }

    public OpaqueSerializer(Class<byte[]> t) {
        super(t);
    }

    @Override
    public void serialize(byte[] bytes, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeObject(new ObjectMapper().readValue(bytes, Map.class));
    }
}

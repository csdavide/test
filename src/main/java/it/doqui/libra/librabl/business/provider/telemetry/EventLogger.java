package it.doqui.libra.librabl.business.provider.telemetry;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import it.doqui.libra.librabl.utils.DateISO8601Utils;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.ZonedDateTime;

@ApplicationScoped
@Slf4j
public class EventLogger {

    private final ObjectMapper mapper;

    public EventLogger() {
        mapper = new ObjectMapper();
        var javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer(DateISO8601Utils.dateFormat));

        SimpleModule module = new SimpleModule();
        module.addSerializer(byte[].class, new NoBufferSerializer());
        mapper.registerModule(module);

        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.disable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE);
    }

    public void log(TraceEvent event) {
        if (mapper != null) {
            try {
                if (event.getException() != null) {
                    event.setResult("Exception: " + event.getException().getMessage());
                    event.setException(null);
                }

                var json = mapper.writeValueAsString(event);
                log.debug(json);
            } catch (Throwable e) {
                log.error("Unable to parse trace event", e);
            }
        }
    }

    public static class NoBufferSerializer extends StdSerializer<byte[]> {

        public NoBufferSerializer() {
            this(null);
        }

        public NoBufferSerializer(Class<byte[]> t) {
            super(t);
        }

        @Override
        public void serialize(byte[] bytes, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            var contentSize = new ContentSize(bytes.length);

            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("size", contentSize.size);
            jsonGenerator.writeEndObject();
        }

        public static class ContentSize {
            private final long size;

            public ContentSize(long size) {
                this.size = size;
            }
        }
    }
}

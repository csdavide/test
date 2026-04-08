package it.doqui.libra.librabl.application.model.events;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

import static it.doqui.libra.librabl.application.model.events.EventType.*;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendEventRequest {
    @Schema(required = true, description = "Event name", enumeration = {RELOAD_TENANT,RELOAD_MIMETYPES,CLEAN_CACHE,SYSTEM_CHECK})
    private final String event;
    private String tenant;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private final Map<String, Object> properties;

    @JsonCreator
    public SendEventRequest(@JsonProperty("event") String event) {
        this.event = event;
        this.properties = new HashMap<>();
    }
}

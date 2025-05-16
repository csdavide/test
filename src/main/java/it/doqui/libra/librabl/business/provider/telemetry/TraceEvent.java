package it.doqui.libra.librabl.business.provider.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceEvent {
    private String serverName;
    private String className;
    private String methodName;
    private TraceCategory category;
    private String tenant;
    private Status status;
    private String dbSchema;
    private String channel;
    private String application;
    private String userIdentity;
    private String operationId;
    private int apiLevel;

    private Exception exception;
    private Duration duration;
    private final Map<String,Object> parameters;

    private Object result;
    private int resultCount;

    public TraceEvent() {
        status = Status.UNKNOWN;
        category = TraceCategory.GENERIC;
        parameters = new LinkedHashMap<>();
    }

    public enum Status {
        UNKNOWN,
        SUCCESS,
        FAILED
    }
}

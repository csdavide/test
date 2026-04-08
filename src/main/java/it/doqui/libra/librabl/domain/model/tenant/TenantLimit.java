package it.doqui.libra.librabl.domain.model.tenant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.doqui.libra.librabl.domain.model.session.SessionMode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantLimit implements Serializable {
    private Operation operation;
    private SessionMode mode;
    private Integer value;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<LimitFeature> features;


    public enum Operation {
        DELETE,
        RENAME,
        LINK
    }

    public enum LimitFeature {
        HIGHER,
        DEFAULT
    }

    public TenantLimit(Operation operation, SessionMode mode, Integer value, Set<LimitFeature> features) {
        this.operation = operation;
        this.mode = mode;
        this.value = value;
        this.features = features;
    }
}

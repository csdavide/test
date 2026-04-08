package it.doqui.libra.librabl.application.model.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantItem {
    private String name;
    private boolean enabled;
    private String temp;
    private String schema;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean indexingDisabled;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean fullTextDisabled;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean tempEphemeralDisabled;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean duplicatesAllowed;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int linkDefaultDurationSeconds;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int linkGracePeriodSeconds;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private Map<String, Object> properties;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String,String> stores;
}

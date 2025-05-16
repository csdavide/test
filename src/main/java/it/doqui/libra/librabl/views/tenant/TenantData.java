package it.doqui.libra.librabl.views.tenant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantData implements Serializable {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Map<String,String> stores;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Set<String> implicitAspects;

    private String defaultStore;
    private String temp;

    @JsonSetter(nulls = Nulls.SKIP)
    private boolean enabled = true;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean numericPathEnabled;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean noPathSupported;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean indexingDisabled;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean fullTextDisabled;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean tempEphemeralDisabled;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean duplicatesAllowed;

    public TenantData() {
        stores = new HashMap<>();
        implicitAspects = new HashSet<>();
    }
}

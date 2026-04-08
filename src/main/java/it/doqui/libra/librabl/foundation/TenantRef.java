package it.doqui.libra.librabl.foundation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantRef {
    public static final String DEFAULT_TENANT = "default";

    private final String name;

    public TenantRef(String name) {
        this.name = StringUtils.isBlank(name) ? DEFAULT_TENANT : name;
    }
    public TenantRef() {
        this.name = DEFAULT_TENANT;
    }

    public static TenantRef valueOf(String name) {
        return new TenantRef(name);
    }

    @Override
    public String toString() {
        return name;
    }
}

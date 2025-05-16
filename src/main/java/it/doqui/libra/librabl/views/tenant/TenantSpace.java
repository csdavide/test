package it.doqui.libra.librabl.views.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class  TenantSpace {
    private String tenant;
    private String schema;
    private Long rootId;
    private TenantData data;

    public boolean isValid() {
        return StringUtils.isNotBlank(schema) && rootId != null;
    }

}

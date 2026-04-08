package it.doqui.libra.librabl.application.model.tenant;

import it.doqui.libra.librabl.domain.model.tenant.TenantLimit;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@ToString
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class TenantCreationRequest {
    private String tenant;
    private String schema;
    private boolean overwrite;
    private char[] password;
    private Map<String,String> stores;
    private Optional<String> temp;
    private Optional<Boolean> indexingDisabled;
    private Optional<Boolean> fullTextDisabled;
    private Optional<Boolean> tempEphemeralDisabled;
    private Optional<Boolean> duplicatesAllowed;
    private Optional<Boolean> tenantIsolationEnabled;
    private List<TenantLimit> limits;
}

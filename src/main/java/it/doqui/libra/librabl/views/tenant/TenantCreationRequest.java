package it.doqui.libra.librabl.views.tenant;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
    private Map<String,String> stores;
    private Optional<String> temp;
    private Optional<Boolean> indexingDisabled;
    private Optional<Boolean> fullTextDisabled;
    private Optional<Boolean> tempEphemeralDisabled;
    private Optional<Boolean> duplicatesAllowed;
}

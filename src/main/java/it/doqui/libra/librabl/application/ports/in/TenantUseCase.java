package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.application.model.tenant.TenantCreationRequest;
import it.doqui.libra.librabl.application.model.tenant.TenantItem;

import java.util.List;
import java.util.Optional;

public interface TenantUseCase {
    List<String> listAllSchemas();
    TenantItem createTenant(TenantCreationRequest request);
    void syncTenant(TenantRef tenantRef, boolean includeAny);
    void deleteTenant(TenantRef tenantRef);
    void performSync(TenantRef tenantRef, boolean includeAny);
    List<TenantItem> findStartingWith(String prefix);
    Optional<TenantItem> findByIdOptional(String name, boolean authRequired);
}

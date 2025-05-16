package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.foundation.TenantRef;
import it.doqui.libra.librabl.views.tenant.TenantCreationRequest;
import it.doqui.libra.librabl.views.tenant.TenantItem;
import it.doqui.libra.librabl.views.tenant.TenantSpace;

import java.util.List;
import java.util.Optional;

public interface TenantService {
    List<String> listAllSchemas();
    TenantSpace createTenant(TenantCreationRequest request);
    void syncTenant(TenantRef tenantRef, boolean includeAny);
    void deleteTenant(TenantRef tenantRef);
    void performSync(TenantRef tenantRef, boolean includeAny);
    List<TenantItem> findStartingWith(String prefix);
    Optional<TenantItem> findByIdOptional(String name);
}

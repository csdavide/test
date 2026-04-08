package it.doqui.libra.librabl.domain.ports.out;

import it.doqui.libra.librabl.domain.model.session.SessionMode;
import it.doqui.libra.librabl.domain.model.tenant.TenantLimit;

import java.util.List;

public interface ConfigurationRepository {
    String getStringProperty(String tenant, String name, boolean defaultLimitValueRequired);
    String getStringProperty(String name, boolean defaultLimitValueRequired);
    Integer getIntegerProperty(String name, boolean defaultLimitValueRequired);
    boolean getBooleanProperty(String name);
    int getLimit(TenantLimit.Operation operation, SessionMode mode, TenantLimit.LimitFeature feature);
    List<String> getAllLimitPropertyKeys();
    TenantLimit mapToTenantLimit(String key, Integer value);
    String mapToConfigPropertyKey(TenantLimit tenantLimit);
}

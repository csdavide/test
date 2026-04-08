package it.doqui.libra.librabl.domain.model.session;

import it.doqui.libra.librabl.domain.model.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Optional;
import java.util.Set;

public interface UserContext extends SecurityContext {

    String ROLE_USER = "user";
    String ROLE_POWERUSER = "poweruser";
    String ROLE_ADMIN = "admin";
    String ROLE_SYSADMIN = "sysadmin";
    String ROLE_SYS = "sys";
    String ROLE_POWERADMIN = "poweradmin";
    String ROLE_SYSMON = "sysmon";
    String CHANNEL_REST = "REST";
    String CHANNEL_JMS = "JMS";
    String CHANNEL_CXF = "CXF";
    String GROUP_EVERYONE = "GROUP_EVERYONE";
    String TENANT_DATA_ATTR = "TENANT";
    String SCOPE_DEFAULT = "default";
    String SCOPE_SYSADMIN = "sysadmin";
    AuthorityRef getAuthorityRef();

    String getDbSchema();
    Set<String> getGroupSet();
    Set<String> getRoleSet();
    Set<String> getScopeSet();
    Object getAttribute(String name);
    ModelSchema getSchema();
    void setSchema(ModelSchema schema);
    UserContext copy();

    default String getAuthority() {
        return Optional.ofNullable(getAuthorityRef()).map(AuthorityRef::toString).orElse(null);
    }

    default boolean isAdmin() {
        return isUserInRole(ROLE_ADMIN);
    }

    default TenantRef getTenantRef() {
        return Optional.ofNullable(getAuthorityRef()).map(AuthorityRef::getTenantRef).orElse(null);
    }
}

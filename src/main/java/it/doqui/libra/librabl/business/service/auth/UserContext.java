package it.doqui.libra.librabl.business.service.auth;

import it.doqui.libra.librabl.business.service.schema.ModelSchema;
import it.doqui.libra.librabl.foundation.AuthorityRef;
import it.doqui.libra.librabl.foundation.TenantRef;

import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;
import java.util.Set;

public interface UserContext extends SecurityContext {

    String ROLE_USER = "User";
    String ROLE_POWERUSER = "PowerUser";
    String ROLE_ADMIN = "Admin";
    String ROLE_SYSADMIN = "SysAdmin";
    String ROLE_POWERADMIN = "PowerAdmin";
    String ROLE_SYSMON = "SysMon";
    String CHANNEL_REST = "REST";
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
    String getOperationId();
    void setOperationId(String operationId);
    ModelSchema getSchema();
    void setSchema(ModelSchema schema);
    String getChannel();
    void setChannel(String channel);
    int getApiLevel();
    void setApiLevel(int level);
    String getApplication();
    void setApplication(String application);
    String getUserIdentity();
    void setUserIdentity(String userIdentity);

    default String getAuthority() {
        return Optional.ofNullable(getAuthorityRef()).map(AuthorityRef::toString).orElse(null);
    }

    default boolean isAdmin() {
        return isUserInRole(ROLE_ADMIN);
    }

    default TenantRef getTenantRef() {
        return Optional.ofNullable(getAuthorityRef()).map(AuthorityRef::getTenantRef).orElse(null);
    }

    Mode getMode();

    enum Mode {
        SYNC,
        ASYNC
    }
}

package it.doqui.libra.librabl.business.service.auth;

import it.doqui.libra.librabl.views.tenant.TenantData;

import java.util.Optional;

public class UserContextManager {

    private UserContextManager() {
        throw new IllegalStateException("Cannot be instantiated");
    }

    private static final ThreadLocal<UserContext> ctx = new ThreadLocal<>();

    public static UserContext getContext() {
        return ctx.get();
    }

    public static void setContext(UserContext context) {
        ctx.set(context);
    }
    public static void removeContext() {
        ctx.remove();
    }

    public static String getTenant() {
        return Optional.ofNullable(getContext())
            .map(c -> c.getTenantRef().toString())
            .orElseThrow(IllegalStateException::new);
    }

    public static Optional<TenantData> getTenantData() {
        return Optional.ofNullable(UserContextManager.getContext())
            .map(c -> c.getAttribute(UserContext.TENANT_DATA_ATTR))
            .map(o -> (TenantData) o);
    }

    public static int getApiLevel() {
        return Optional.ofNullable(UserContextManager.getContext())
            .map(UserContext::getApiLevel)
            .orElse(0);
    }

    public static boolean hasMonitorRole() {
        return Optional.ofNullable(UserContextManager.getContext())
            .map(c -> c.isUserInRole(UserContext.ROLE_SYSMON))
            .orElse(false);
    }

}

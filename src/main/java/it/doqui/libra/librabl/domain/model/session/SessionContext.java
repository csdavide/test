package it.doqui.libra.librabl.domain.model.session;

import it.doqui.libra.librabl.domain.model.jobs.JobContext;
import it.doqui.libra.librabl.domain.model.tenant.TenantData;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public interface SessionContext extends JobContext {
    UserContext getUserContext();
    SessionMode getMode();
    void setMode(SessionMode mode);
    String getOperationId();
    void setOperationId(String operationId);
    String getChannel();
    void setChannel(String channel);
    int getApiLevel();
    void setApiLevel(int level);
    String getApplication();
    void setApplication(String application);
    String getUserIdentity();
    void setUserIdentity(String userIdentity);
    AtomicLong getOperationCounter();
    String getToken();
    void setToken(String token);

    AtomicBoolean getCancelled();
    default String getJobId() {
        return getMode() == SessionMode.ASYNC ? getOperationId() : null;
    }

    default Optional<TenantData> getTenantData() {
        return Optional.ofNullable(getUserContext())
                .map(c -> c.getAttribute(UserContext.TENANT_DATA_ATTR))
                .map(o -> (TenantData) o);
    }

    default String getTenant() {
        return Optional.ofNullable(getUserContext())
                .map(c -> c.getTenantRef().toString())
                .orElseThrow(IllegalStateException::new);
    }

    static boolean hasRole(SessionContext context, String role) {
        return Optional.ofNullable(context).map(SessionContext::getUserContext).map(c -> c.isUserInRole(role)).orElse(false);
    }
}

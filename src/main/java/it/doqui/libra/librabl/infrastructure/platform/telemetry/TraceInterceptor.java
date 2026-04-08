package it.doqui.libra.librabl.infrastructure.platform.telemetry;


import it.doqui.libra.librabl.domain.model.session.SessionContext;
import it.doqui.libra.librabl.domain.model.session.UserContext;
import it.doqui.libra.librabl.foundation.Paged;
import it.doqui.libra.librabl.foundation.telemetry.TraceCategory;
import it.doqui.libra.librabl.foundation.telemetry.TraceParam;
import it.doqui.libra.librabl.foundation.telemetry.Traceable;
import it.doqui.libra.librabl.infrastructure.platform.boot.Bootstrapper;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Interceptor
@Traceable
@Priority(42)
@Slf4j
public class TraceInterceptor {

    @Inject
    @Any
    Event<TraceEvent> trigger;

    @Inject
    SessionContext sessionContext;

    @Inject
    Bootstrapper bootstrapper;

    private static final ThreadLocal<TraceEvent> processingEvent = new ThreadLocal<>();

    @AroundInvoke
    public Object trace(InvocationContext invocationContext) throws Exception {
        if (processingEvent.get() != null || SessionContext.hasRole(sessionContext, UserContext.ROLE_SYSMON)) {
            return invocationContext.proceed();
        }

        var m = invocationContext.getMethod();
        var methodTraceable = m.getDeclaredAnnotation(Traceable.class);
        var classTraceable = m.getDeclaringClass().getDeclaredAnnotation(Traceable.class);
        if (methodTraceable == null && classTraceable == null
            || (methodTraceable != null && methodTraceable.ignore())
            || (methodTraceable == null && classTraceable.ignore())) {
            return invocationContext.proceed();
        }

        var event = new TraceEvent();
        event.setServerName(bootstrapper.getHostName());
        if (methodTraceable == null || "".equals(methodTraceable.name())) {
            var methodName = m.getName();
            if (m.getParameters() != null && m.getParameters().length > 0) {
                methodName += Arrays.stream(m.getParameters())
                    .map(p -> Optional.ofNullable(p.getAnnotation(TraceParam.class))
                        .filter(a -> !"".equals(a.value()))
                        .map(TraceParam::value)
                        .orElse(p.getName()))
                    .collect(Collectors.joining("#", "(", ")"));
            }
            event.setMethodName(methodName);
        } else {
            event.setMethodName(methodTraceable.name());
        }

        event.setClassName(
            (classTraceable != null && !"".equals(classTraceable.name()))
                ? classTraceable.name()
                : m.getDeclaringClass().getSimpleName()
        );

        event.setCategory(
            Optional.ofNullable(methodTraceable)
                .map(Traceable::category)
                .orElse(
                    Optional.ofNullable(classTraceable)
                        .map(Traceable::category)
                        .orElse(TraceCategory.GENERIC)
                )
        );

        if (m.getParameters() != null) {
            var traceAllParameters = methodTraceable != null && methodTraceable.traceAllParameters();
            for (int i = 0; i < m.getParameterCount(); i++) {
                var p = m.getParameters()[i];
                var t = Optional.ofNullable(p.getAnnotation(TraceParam.class))
                    .map(x -> !x.ignore())
                    .orElse(traceAllParameters);

                if (t) {
                    var value = invocationContext.getParameters()[i];
                    event.getParameters().put(p.getName(), value);
                }
            }
        }

        long start = System.currentTimeMillis();
        processingEvent.set(event);
        try {
            var result = invocationContext.proceed();
            event.setStatus(TraceEvent.Status.SUCCESS);
            if (methodTraceable != null && methodTraceable.traceResult()) {
                event.setResult(result);
            }

            if (result != null) {
                if (result instanceof Collection<?> collection) {
                    event.setResultCount(collection.size());
                } else if (result instanceof Paged<?> paged) {
                    event.setResultCount(paged.getItems().size());
                } else {
                    event.setResultCount(1);
                }
            }

            return result;
        } catch (Exception e) {
            event.setStatus(TraceEvent.Status.FAILED);
            event.setException(e);
            throw e;
        } finally {
            processingEvent.remove();
            event.setDuration(Duration.ofMillis(System.currentTimeMillis() - start));
            var userContext = sessionContext.getUserContext();
            if (userContext != null) {
                event.setDbSchema(userContext.getDbSchema());

                if (userContext.getTenantRef() != null) {
                    event.setTenant(userContext.getTenantRef().toString());
                }
            }

            if (sessionContext != null) {
                event.setOperationId(sessionContext.getOperationId());
                event.setSessionMode(sessionContext.getMode());
                event.setChannel(sessionContext.getChannel());
                event.setApiLevel(sessionContext.getApiLevel());
                event.setApplication(sessionContext.getApplication());
                event.setUserIdentity(sessionContext.getUserIdentity());
            }
            trigger.fire(event);
        }
    }
}

package it.doqui.libra.librabl.api.v1.cxf.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.All;
import it.doqui.index.ecmengine.mtom.exception.EcmEngineException;
import it.doqui.libra.librabl.api.v1.cxf.ServiceProxy;
import it.doqui.libra.librabl.business.service.auth.UserContextManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class ServiceDispatcher implements InvocationHandler {

    @ConfigProperty(name = "libra.log.cxf.args.enabled", defaultValue = "false")
    boolean traceEnabled;

    public ServiceProxy getProxy() {
        return (ServiceProxy) Proxy.newProxyInstance(ServiceDispatcher.class.getClassLoader(), new Class<?>[]{ ServiceProxy.class }, this);
    }

    @Inject
    @All
    List<AbstractServiceBridge> bridges;

    @Inject
    ObjectMapper objectMapper;

    final Map<String, Pair<Object,Method>> wrappedMethods = new HashMap<>();

    @PostConstruct
    public void init() {
        if (bridges != null) {
            bridges.forEach(bridge -> {
                for (Method m : bridge.getClass().getMethods()) {
                    wrappedMethods.put(m.getName(), new ImmutablePair<>(bridge, m));
                }
            });
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return invoke(method.getName(), args);
    }

    public Object invoke(String methodName, Object[] args) throws Throwable {
        Pair<Object,Method> p = wrappedMethods.get(methodName);
        if (p != null) {
            var m = p.getRight();
            long t0 = System.currentTimeMillis();
            try {
                log.info("[ServiceImpl::{}] BEGIN", methodName);
                return m.invoke(p.getLeft(), args);
            } catch (InvocationTargetException e) {
                if (traceEnabled) {
                    try {
                        log.debug("[ServiceImpl::{}] FAILED", m.getName());
                        for (int i = 0; i < args.length; i++) {
                            final String paramName;
                            if (i < m.getParameterCount()) {
                                var param = m.getParameters()[i];
                                paramName = param.getName();
                            } else {
                                paramName = "{arg" + i + "}";
                            }

                            var value = objectMapper.writeValueAsString(args[i]);
                            log.debug("[ServiceImpl::{}] {} = '{}'", m.getName(), paramName, value);
                        }
                    } catch (Exception ignore) {
                        // ignore
                    }
                }

                if (e.getTargetException() != null) {
                    throw e.getTargetException();
                } else if (e.getCause() != null) {
                    throw e.getCause();
                }

                throw e;
            } finally {
                log.info("[ServiceImpl::{}] END in {} millis", methodName, (System.currentTimeMillis() - t0));
                UserContextManager.removeContext();
            }
        } else {
            throw new EcmEngineException("method not yet implemented");
        }
    }
}

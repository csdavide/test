package it.doqui.libra.librabl.foundation.flow;

import io.quarkus.arc.All;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class BusinessDispatcher implements InvocationHandler {

    @Inject
    @All
    @SuppressWarnings("CdiInjectionPointsInspection")
    List<BusinessComponent> components;

    @Inject
    BusinessContext businessContext;

    final Map<Pair<String,String>, Pair<BusinessComponent,Method>> wrappedMethods = new HashMap<>();

    @PostConstruct
    public void init() {
        if (components != null) {
            components.forEach(component -> {
                var clazz = component.getComponentInterface();
                for (Method m : component.getClass().getMethods()) {
                    var key = new ImmutablePair<>(clazz.getSimpleName(), m.getName());
                    var value = new ImmutablePair<>(component, m);
                    wrappedMethods.put(key, value);
                }
            });
        }
    }

    public <T> T getProxy(Class<T> clazz) {
        var proxy = Proxy.newProxyInstance(BusinessDispatcher.class.getClassLoader(), new Class<?>[]{ clazz }, this);
        return clazz.cast(proxy);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String className = Arrays.stream(proxy.getClass().getInterfaces())
            .map(Class::getSimpleName)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Not yet implemented"));
        final String methodName = method.getName();

        var key = new ImmutablePair<>(className, methodName);
        var p = wrappedMethods.get(key);
        if (p != null) {
            long t0 = System.currentTimeMillis();
            try {
                businessContext.getStack().addFirst(new BusinessContext.InvocationCall(className, methodName));
                log.info("[{}::{}] BEGIN", className, methodName);
                var r = p.getRight().invoke(p.getLeft(), args);
                businessContext.getStack().removeFirst();
                return r;
            } catch (InvocationTargetException e) {
                if (e.getTargetException() != null) {
                    throw e.getTargetException();
                } else if (e.getCause() != null) {
                    throw e.getCause();
                }

                throw e;
            } finally {
                log.info("[{}::{}] END in {} millis", className, methodName, (System.currentTimeMillis() - t0));
            }
        } else {
            throw new SystemException("service not yet implemented");
        }
    }
}

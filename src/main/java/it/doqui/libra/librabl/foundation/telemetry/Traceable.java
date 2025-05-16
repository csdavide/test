package it.doqui.libra.librabl.foundation.telemetry;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@InterceptorBinding
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface Traceable {
    @Nonbinding String name() default "";
    @Nonbinding TraceCategory category() default TraceCategory.GENERIC;
    @Nonbinding boolean ignore() default false;
    @Nonbinding boolean traceAllParameters() default false;
    @Nonbinding boolean traceResult() default false;
}

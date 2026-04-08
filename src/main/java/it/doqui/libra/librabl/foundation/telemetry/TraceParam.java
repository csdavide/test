package it.doqui.libra.librabl.foundation.telemetry;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(PARAMETER)
@Retention(RUNTIME)
public @interface TraceParam {
    String value() default "";
    boolean ignore() default false;
}

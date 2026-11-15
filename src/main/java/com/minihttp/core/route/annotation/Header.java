package com.minihttp.core.route.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Header {
    String value() default "";

    String defaultValue() default AnnotationDefaults.NO_DEFAULT_VALUE;
}

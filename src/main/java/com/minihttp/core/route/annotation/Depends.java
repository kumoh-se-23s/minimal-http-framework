package com.minihttp.core.route.annotation;

import com.minihttp.core.route.dependency.Dependency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Depends {
    Class<? extends Dependency<?>> value();
}

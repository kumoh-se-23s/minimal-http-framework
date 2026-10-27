package com.minihttp.core.route.annotation;

import com.minihttp.packet.protocol.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HttpMapping(HttpMethod.GET)
public @interface Get {
    String value() default "";
}

package com.minihttp.core.routing.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 경로 패턴의 {@code {name}} 자리 값을 이 파라미터에 넣는다. 이름을 생략하면 파라미터 이름을 쓴다.
 * 파라미터 이름이 패턴의 변수와 같으면 이 어노테이션 없이도 경로 파라미터로 묶인다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PathParam {
    String value() default "";
}

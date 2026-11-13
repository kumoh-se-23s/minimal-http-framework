package com.minihttp.core.route.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 경로의 {@code {변수}} 값을 받는다. FastAPI의 {@code Path()}.
 *
 * <p>파라미터 이름이 경로 변수 이름과 같으면 붙이지 않아도 된다. 이름이 다를 때 {@code @Path("projectId") Long id}처럼 쓴다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Path {
    /** 경로 변수 이름. 비어 있으면 파라미터 이름을 쓴다. */
    String value() default "";
}

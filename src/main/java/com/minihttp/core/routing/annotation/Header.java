package com.minihttp.core.routing.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 헤더에서 값을 꺼내 이 파라미터에 넣는다.
 * <p>이름을 생략하면 파라미터 이름을 쓴다. 헤더는 {@code contentType → content-Type}처럼 바꿔서 찾는다 (대소문자는 구분하지 않는다).
 * 값이 없으면 {@link #defaultValue()}를 쓰고, 그것도 없으면 400. 파라미터가 {@code Optional}이면 비어서 들어온다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Header {
    /** defaultValue를 지정하지 않았다는 표시. 실제 값과 겹치지 않게 NUL로 감싼다. */
    String NONE = "\u0000none\u0000";

    String value() default "";

    String defaultValue() default NONE;
}

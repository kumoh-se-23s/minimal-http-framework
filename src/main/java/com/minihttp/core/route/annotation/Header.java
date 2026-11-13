package com.minihttp.core.route.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 헤더 값을 받는다. FastAPI의 {@code Header()}.
 *
 * <p>이름을 지정하지 않으면 FastAPI가 {@code user_agent → user-agent}로 바꾸듯, 카멜 케이스 파라미터 이름을
 * 하이픈으로 바꿔서 찾는다: {@code @Header String userAgent → "user-agent"}. 헤더 이름은 대소문자를 구분하지 않는다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Header {
    /** 헤더 이름. 비어 있으면 파라미터 이름을 하이픈 형식으로 바꿔서 쓴다. */
    String value() default "";

    /** 값이 없을 때 쓸 기본값. 지정하면 선택 파라미터가 된다. */
    String defaultValue() default AnnotationDefaults.NO_DEFAULT_VALUE;
}

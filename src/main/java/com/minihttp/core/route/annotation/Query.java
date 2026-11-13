package com.minihttp.core.route.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 쿼리 스트링 값을 받는다. FastAPI의 {@code Query()}.
 *
 * <p>단일 값 타입(String, 숫자, enum, 날짜 ...)과 그 List/Set은 붙이지 않아도 쿼리 파라미터가 된다.
 * 이름을 바꾸거나 기본값을 줄 때 쓴다: {@code @Query(value = "page-size", defaultValue = "20") int pageSize}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Query {
    /** 쿼리 키. 비어 있으면 파라미터 이름을 쓴다. FastAPI의 alias. */
    String value() default "";

    /** 값이 없을 때 쓸 기본값. 지정하면 선택 파라미터가 된다. */
    String defaultValue() default AnnotationDefaults.NO_DEFAULT_VALUE;
}

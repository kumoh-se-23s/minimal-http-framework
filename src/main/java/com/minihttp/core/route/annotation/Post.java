package com.minihttp.core.route.annotation;

import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * POST 엔드포인트. FastAPI의 {@code @router.post(path, status_code=...)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HttpMapping(HttpMethod.POST)
public @interface Post {
    /** 라우터 prefix 뒤에 붙는 경로. {@code {변수}}로 path 파라미터를 받는다. */
    String value() default "";

    /** 반환값을 JSON으로 보낼 때의 상태 코드. 반환값이 HttpResponse면 그 응답의 상태 코드를 따른다. */
    HttpStatus status() default HttpStatus.OK;
}

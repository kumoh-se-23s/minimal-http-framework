package com.minihttp.core.route.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 body를 받는다. FastAPI의 {@code Body()}.
 *
 * <p>DTO, record, Map, List&lt;DTO&gt; 같은 타입은 붙이지 않아도 JSON body가 된다.
 * 단일 값 타입(String, int ...)을 query가 아닌 body로 받고 싶을 때 붙인다.
 * 타입이 {@code byte[]}면 JSON으로 해석하지 않고 body 바이트를 그대로 넘긴다. (이미지 업로드 등)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Body {
}

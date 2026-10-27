package com.minihttp.core.routing.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 요청 본문을 이 파라미터에 넣는다. 지금은 {@code byte[]}와 {@code String}(UTF-8)만 받는다. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Body {
}

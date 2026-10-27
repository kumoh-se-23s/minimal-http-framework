package com.minihttp.core.routing.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** DELETE 요청을 이 메서드로 보낸다. 값은 경로 패턴이며 {name} 자리는 경로 파라미터가 된다. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Delete {
    String value();
}

package com.minihttp.core.route.annotation;

import com.minihttp.core.route.dependency.Dependency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link Dependency}가 요청에서 만들어 낸 값을 받는다. FastAPI의 {@code Depends()}.
 *
 * <pre>
 *   &#64;Get("/me")
 *   public UserResponse me(&#64;Depends(CurrentUserId.class) Long userId) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Depends {
    Class<? extends Dependency<?>> value();
}

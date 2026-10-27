package com.minihttp.core.routing.binding;

import com.minihttp.packet.HttpRequest;

/** 요청에서 메서드 파라미터 하나의 값을 꺼낸다. 값이 없거나 잘못됐으면 {@link BadParameterException}. */
@FunctionalInterface
interface ArgumentResolver {
    Object resolve(HttpRequest request);
}

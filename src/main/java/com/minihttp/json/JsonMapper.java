package com.minihttp.json;

import java.lang.reflect.Type;

/**
 * 프레임워크가 요청 body를 읽고 반환값을 쓸 때 쓰는 JSON 변환기.
 * 기본은 의존성 없는 {@link DefaultJsonMapper}이고, Jackson 같은 라이브러리로 바꾸고 싶으면 이 인터페이스를 구현해서 등록한다.
 */
public interface JsonMapper {
    String write(Object value);

    /**
     * @throws JsonException JSON이 깨졌거나 타입에 맞지 않을 때. 프레임워크가 422 응답으로 바꾼다.
     */
    Object read(String json, Type targetType);
}

package com.minihttp.core.routing.binding;

/** 요청 값이 없거나 타입 변환에 실패함. 클라이언트 잘못이므로 400 응답으로 바뀐다. */
final class BadParameterException extends RuntimeException {
    BadParameterException(String message) {
        super(message);
    }
}

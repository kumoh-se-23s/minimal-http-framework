package com.minihttp.example.http;

/**
 * 요청을 파싱하다 실패했을 때 던진다. 클라이언트에게 돌려줄 상태 코드를 함께 담는다.
 */
public class HttpParseException extends RuntimeException {
    private final HttpStatus status;

    public HttpParseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

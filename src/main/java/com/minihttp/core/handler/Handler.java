package com.minihttp.core.handler;

import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;


/**
 * 요청 하나를 받아 응답 하나를 만든다. 서버 코어와 애플리케이션(라우팅, 비즈니스 로직)의 경계.
 * 소켓, 연결, keep-alive는 전혀 모른다.
 *
 * <p>참고 예제: Step07KeepAliveExample#handle의 "3) 응답 쓰기" 직전에 body를 만드는 부분이 이 자리다
 * (example 모듈 {@code com.minihttp.example.socket}). 거기서는 요청을 그대로 되돌려 주는 고정 로직이다.
 */
@FunctionalInterface
public interface Handler {
    /**
     * @return 이 요청에 대한 응답. null이면 안 된다 (처리할 경로가 없으면 404 같은 응답을 직접 만든다).
     * @throws Exception 처리 중 실패. Connection이 잡아서 500 응답으로 바꾼다.
     */
    HttpResponse handle(HttpRequest request) throws Exception;
}

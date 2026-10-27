package com.minihttp.example.routing_example;

import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * 라우팅 계층이 직접 만드는 응답 (404, 405, 400, 컨트롤러가 돌려준 String/void 등).
 *
 * <p>버전은 HTTP/1.1로 고정한다. Connection의 에러 응답도 같은 방식이라 맞춰 둔다.
 * 요청 버전을 따라가게 하려면 여기에 request를 넘겨 받으면 된다.
 */
final class RoutingResponses {
    private RoutingResponses() {
    }

    static HttpResponse text(@NonNull HttpStatus status, @NonNull String text) {
        HttpResponse response = new HttpResponse(
                HttpVersion.HTTP_1_1,
                new DefaultHttpHeader(),
                new DefaultHttpBody(text.getBytes(StandardCharsets.UTF_8)),
                status
        );
        response.contentType(ContentType.TEXT_PLAIN);
        return response;
    }

    static HttpResponse empty(@NonNull HttpStatus status) {
        return new HttpResponse(HttpVersion.HTTP_1_1, new DefaultHttpHeader(), new DefaultHttpBody(), status);
    }
}

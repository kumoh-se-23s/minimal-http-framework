package com.minihttp.core.routing.support;

import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;

/** 라우팅 계층이 직접 만드는 응답 (404, 405, 400, 컨트롤러가 돌려준 String/void). 컨트롤러가 HttpResponse를 만들 때 써도 된다. */
public final class RoutingResponses {
    private RoutingResponses() {
    }

    public static HttpResponse text(@NonNull HttpStatus status, @NonNull String text) {
        HttpResponse response = new HttpResponse(
                HttpVersion.HTTP_1_1,
                new DefaultHttpHeader(),
                new DefaultHttpBody(text.getBytes(StandardCharsets.UTF_8)),
                status
        );
        response.contentType(ContentType.TEXT_PLAIN);
        return response;
    }

    public static HttpResponse empty(@NonNull HttpStatus status) {
        return new HttpResponse(HttpVersion.HTTP_1_1, new DefaultHttpHeader(), new DefaultHttpBody(), status);
    }
}

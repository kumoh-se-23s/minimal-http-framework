package com.minihttp.core.response;

import com.minihttp.json.JsonWriter;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;


public class JsonResponse extends HttpResponse {

    public JsonResponse(Object content) {
        this(HttpStatus.OK, content);
    }

    public JsonResponse(@NonNull HttpStatus status, Object content) {
        super(status);
        this.body().set(JsonWriter.write(content).getBytes(StandardCharsets.UTF_8));
        this.contentType(ContentType.APPLICATION_JSON);
    }

    public static JsonResponse fromJsonText(@NonNull HttpStatus status, @NonNull String jsonText) {
        JsonResponse response = new JsonResponse(status, null);
        response.body().set(jsonText.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}

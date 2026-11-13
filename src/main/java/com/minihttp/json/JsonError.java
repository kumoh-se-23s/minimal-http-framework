package com.minihttp.json;

import java.util.List;

/**
 * JSON 에러 하나.
 *
 * @param errorType FastAPI(pydantic)의 에러 타입 이름 (ex. json_invalid, missing, int_parsing)
 * @param location  문제가 난 위치. 파싱 실패면 [문자 위치], 바인딩 실패면 ["필드", 0, "하위 필드"] 같은 경로.
 * @param message   사람이 읽을 설명
 */
public record JsonError(String errorType, List<Object> location, String message) {
    public JsonError {
        location = List.copyOf(location);
    }
}

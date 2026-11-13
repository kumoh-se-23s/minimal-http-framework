package com.minihttp.core.route.exception;

import java.util.List;

/**
 * 요청 값 검증 실패 하나. 422 응답의 detail 배열 원소가 된다.
 *
 * @param errorType FastAPI(pydantic)의 에러 타입 (ex. missing, int_parsing, json_invalid)
 * @param location  ["query", "limit"], ["body", "rewards", 0, "price"] 같은 위치
 * @param message   사람이 읽을 설명
 */
public record ValidationError(String errorType, List<Object> location, String message) {
    public ValidationError {
        location = List.copyOf(location);
    }
}

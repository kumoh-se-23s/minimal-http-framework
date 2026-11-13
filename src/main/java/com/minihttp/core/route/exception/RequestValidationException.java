package com.minihttp.core.route.exception;

import java.util.List;
import java.util.stream.Collectors;

/**
 * path, query, header, body 값이 엔드포인트 파라미터에 맞지 않을 때. 기본 핸들러가 422로 응답한다.
 * FastAPI의 {@code RequestValidationError}.
 */
public class RequestValidationException extends RuntimeException {
    private final List<ValidationError> errors;

    public RequestValidationException(List<ValidationError> errors) {
        super(RequestValidationException.describe(errors), null, false, false);
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return this.errors;
    }

    private static String describe(List<ValidationError> errors) {
        return errors.stream()
                     .map(error -> error.location() + ": " + error.message())
                     .collect(Collectors.joining(", "));
    }
}

package com.minihttp.core.route.binding;

/** 엔드포인트 파라미터 값을 요청의 어디에서 가져오는지. */
public enum ParameterSource {
    /** 경로의 {변수} */
    PATH("path"),
    /** 쿼리 스트링 */
    QUERY("query"),
    /** 요청 헤더 */
    HEADER("header"),
    /** 요청 body (JSON 또는 byte[] 그대로) */
    BODY("body"),
    /** HttpRequest 객체 그 자체 */
    REQUEST("request"),
    /** {@code @Depends}로 지정한 Dependency의 결과 */
    DEPENDENCY("dependency");

    private final String locationName;

    ParameterSource(String locationName) {
        this.locationName = locationName;
    }

    /** 422 응답의 loc 첫 번째 원소. FastAPI와 같은 이름. */
    public String locationName() {
        return this.locationName;
    }
}

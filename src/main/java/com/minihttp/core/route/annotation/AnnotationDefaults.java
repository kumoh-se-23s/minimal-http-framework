package com.minihttp.core.route.annotation;

/**
 * 어노테이션 속성은 null을 기본값으로 쓸 수 없어서, "기본값을 지정하지 않음"을 나타내는 표식 문자열을 둔다.
 */
public final class AnnotationDefaults {
    /** 실제 요청 값으로 들어올 일이 없는 문자열. */
    public static final String NO_DEFAULT_VALUE = "\n\t\n__minihttp_no_default_value__\n\t\n";

    private AnnotationDefaults() {
    }
}

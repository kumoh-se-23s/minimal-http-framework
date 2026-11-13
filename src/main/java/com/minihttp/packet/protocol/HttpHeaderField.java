package com.minihttp.packet.protocol;

public enum HttpHeaderField {
    ACCESS_CONTROL_ALLOW_CREDENTIALS("Access-Control-Allow-Credentials"),
    ACCESS_CONTROL_ALLOW_HEADERS("Access-Control-Allow-Headers"),
    ACCESS_CONTROL_ALLOW_METHODS("Access-Control-Allow-Methods"),
    ACCESS_CONTROL_ALLOW_ORIGIN("Access-Control-Allow-Origin"),
    ACCESS_CONTROL_EXPOSE_HEADERS("Access-Control-Expose-Headers"),
    ACCESS_CONTROL_MAX_AGE("Access-Control-Max-Age"),
    ACCESS_CONTROL_REQUEST_HEADERS("Access-Control-Request-Headers"),
    ACCESS_CONTROL_REQUEST_METHOD("Access-Control-Request-Method"),
    ALLOW("Allow"),
    CONNECTION("Connection"),
    CONTENT_LENGTH("Content-Length"),
    CONTENT_TYPE("Content-Type"),
    DATE("Date"),
    HOST("Host"),
    ORIGIN("Origin"),
    SERVER("Server"),
    TRANSFER_ENCODING("Transfer-Encoding"),
    VARY("Vary");

    private final String fieldName;

    HttpHeaderField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return this.fieldName;
    }
}

package com.minihttp.packet.protocol;

public enum HttpHeaderField {
    ALLOW("Allow"),
    CONNECTION("Connection"),
    CONTENT_LENGTH("Content-Length"),
    CONTENT_TYPE("Content-Type"),
    DATE("Date"),
    HOST("Host"),
    SERVER("Server"),
    TRANSFER_ENCODING("Transfer-Encoding");

    private final String fieldName;

    HttpHeaderField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return this.fieldName;
    }
}

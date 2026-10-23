package com.minihttp.packet.protocol;

public enum HttpVersion {
    HTTP_1_0("HTTP/1.0", false),
    HTTP_1_1("HTTP/1.1", true);

    private final String token;
    private final boolean defaultKeepAlive;

    HttpVersion(String token, boolean defaultKeepAlive) {
        this.token = token;
        this.defaultKeepAlive = defaultKeepAlive;
    }

    public static HttpVersion from(String token) {
        for (HttpVersion version : values()) {
            if (version.token.equals(token)) {
                return version;
            }
        }
        throw new HttpParseException(HttpStatus.HTTP_VERSION_NOT_SUPPORTED, "Unsupported version: " + token);
    }

    public String token() {
        return this.token;
    }

    public boolean defaultKeepAlive() {
        return this.defaultKeepAlive;
    }
}

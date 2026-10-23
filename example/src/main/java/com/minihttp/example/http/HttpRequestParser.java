package com.minihttp.example.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 바이트 스트림에서 HTTP/1.1 요청 하나를 읽는다.
 *
 * <pre>
 * GET /hello?name=kim HTTP/1.1\r\n     ← 요청 라인
 * Host: localhost\r\n                  ← 헤더들
 * Content-Length: 5\r\n
 * \r\n                                 ← 빈 줄 = 헤더 끝
 * hello                                ← 바디 (Content-Length 바이트만큼)
 * </pre>
 *
 * 바디 길이는 문자 수가 아니라 바이트 수라서 Reader가 아닌 InputStream으로 직접 읽는다.
 */
public final class HttpRequestParser {
    private static final int MAX_REQUEST_LINE_LENGTH = 8 * 1024;
    private static final int MAX_HEADER_LINE_LENGTH = 8 * 1024;
    private static final int MAX_HEADER_COUNT = 100;
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;

    /**
     * @return 파싱한 요청. 요청을 시작하기 전에 연결이 닫혔으면 null.
     */
    public HttpRequest parse(InputStream in) throws IOException {
        String requestLine = readLine(in, MAX_REQUEST_LINE_LENGTH, HttpStatus.URI_TOO_LONG);
        // RFC 9112 2.2: 요청 라인 앞의 빈 줄은 무시해도 된다.
        while (requestLine != null && requestLine.isEmpty()) {
            requestLine = readLine(in, MAX_REQUEST_LINE_LENGTH, HttpStatus.URI_TOO_LONG);
        }
        if (requestLine == null) {
            return null;
        }

        String[] parts = requestLine.split(" ", -1);
        if (parts.length != 3) {
            throw new HttpParseException(HttpStatus.BAD_REQUEST, "Malformed request line");
        }
        HttpMethod method = HttpMethod.from(parts[0]);
        String target = parts[1];
        String version = parts[2];
        if (!target.startsWith("/")) {
            throw new HttpParseException(HttpStatus.BAD_REQUEST, "Request target must start with '/'");
        }
        if (!version.equals("HTTP/1.1") && !version.equals("HTTP/1.0")) {
            throw new HttpParseException(HttpStatus.HTTP_VERSION_NOT_SUPPORTED, "Unsupported version: " + version);
        }

        HttpHeaders headers = readHeaders(in);
        byte[] body = readBody(in, headers);

        int queryStart = target.indexOf('?');
        String rawPath = queryStart < 0 ? target : target.substring(0, queryStart);
        String rawQuery = queryStart < 0 ? "" : target.substring(queryStart + 1);

        return new HttpRequest(method, decodePath(rawPath), parseQuery(rawQuery), version, headers, body, Map.of());
    }

    private HttpHeaders readHeaders(InputStream in) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        int count = 0;
        while (true) {
            String line = readLine(in, MAX_HEADER_LINE_LENGTH, HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE);
            if (line == null) {
                throw new HttpParseException(HttpStatus.BAD_REQUEST, "Connection closed before end of headers");
            }
            if (line.isEmpty()) {
                return headers;
            }
            if (++count > MAX_HEADER_COUNT) {
                throw new HttpParseException(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE, "Too many headers");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new HttpParseException(HttpStatus.BAD_REQUEST, "Malformed header: " + line);
            }
            String name = line.substring(0, colon);
            if (!name.equals(name.strip()) || name.contains(" ")) {
                throw new HttpParseException(HttpStatus.BAD_REQUEST, "Whitespace in header name");
            }
            headers.add(name, line.substring(colon + 1).strip());
        }
    }

    private byte[] readBody(InputStream in, HttpHeaders headers) throws IOException {
        if (headers.contains("Transfer-Encoding")) {
            throw new HttpParseException(HttpStatus.NOT_IMPLEMENTED, "Transfer-Encoding is not supported");
        }
        String contentLength = headers.get("Content-Length").orElse(null);
        if (contentLength == null) {
            return new byte[0];
        }

        long length;
        try {
            length = Long.parseLong(contentLength);
        } catch (NumberFormatException e) {
            throw new HttpParseException(HttpStatus.BAD_REQUEST, "Invalid Content-Length");
        }
        if (length < 0) {
            throw new HttpParseException(HttpStatus.BAD_REQUEST, "Invalid Content-Length");
        }
        if (length > MAX_BODY_SIZE) {
            throw new HttpParseException(HttpStatus.PAYLOAD_TOO_LARGE, "Body too large");
        }

        byte[] body = in.readNBytes((int) length);
        if (body.length < length) {
            throw new HttpParseException(HttpStatus.BAD_REQUEST, "Body shorter than Content-Length");
        }
        return body;
    }

    /**
     * '\n'까지 읽고 끝의 '\r'을 떼어낸다. 헤더는 ISO-8859-1 바이트로 취급한다.
     *
     * @return 한 줄. 아무것도 읽기 전에 스트림이 끝났으면 null.
     */
    private String readLine(InputStream in, int maxLength, HttpStatus tooLongStatus) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (line.size() == 0) {
                    return null;
                }
                throw new HttpParseException(HttpStatus.BAD_REQUEST, "Unexpected end of stream");
            }
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            if (line.size() >= maxLength) {
                throw new HttpParseException(tooLongStatus, "Line too long");
            }
            line.write(b);
        }
    }

    private String decodePath(String rawPath) {
        // URLDecoder는 '+'를 공백으로 바꾸는데, 경로에서 '+'는 그냥 '+'다.
        return decode(rawPath.replace("+", "%2B"));
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new HttpParseException(HttpStatus.BAD_REQUEST, "Malformed percent-encoding");
        }
    }
}

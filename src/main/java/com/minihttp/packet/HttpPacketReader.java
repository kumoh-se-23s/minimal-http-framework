package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpParseException;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class HttpPacketReader {
    private static final byte[] HEADER_END_BYTES = {'\r', '\n', '\r', '\n'};
    private static final long MAX_BODY_BYTES = 10L * 1024 * 1024;

    private final InputStream inputStream;
    private final byte[] buffer;
    private int start = 0;
    private int end = 0;

    public HttpPacketReader(InputStream inputStream, byte[] buffer) {
        this.inputStream = inputStream;
        this.buffer = buffer;
    }

    public boolean awaitRequest() throws IOException {
        if (this.start < this.end) {
            return true;
        }

        return this.fillBuffer();
    }

    public HttpRequest readPacket() throws IOException {
        // 헤더를 분리해서 문자열로 변환 및 start index 이동
        int headerEndIndex = this.readHeader();
        String headerString = new String(
                this.buffer,
                this.start, headerEndIndex - HEADER_END_BYTES.length - this.start,
                StandardCharsets.ISO_8859_1
        );

        this.start = headerEndIndex;

        // headerLines는 0번에 request-line(start-line이라고도 불림)이 위치,
        // 이후에는 field-line이 이어짐.
        String[] headerLines = headerString.split("\r\n", -1);
        String[] requestLine = parseRequestLine(headerLines[0]);

        HttpMethod requestMethod = HttpMethod.from(requestLine[0]);
        HttpVersion httpVersion = HttpVersion.from(requestLine[2]);

        // RFC 9112 3.2에 의해서 Host 필드를 가지지 않은 1.1버전의 헤더는 거부.
        HttpHeader httpHeader = parseHeaders(headerLines);
        if (httpVersion == HttpVersion.HTTP_1_1 && !httpHeader.contains("Host")) {
            throw createParseException(HttpStatus.BAD_REQUEST, "Missing header field: \"Host\"");
        }

        HttpBody httpBody = new DefaultHttpBody(readBody(httpHeader));

        // target에서 endpoint와 QSP를 분리.
        String targetEndpointPath = requestLine[1];
        int questionCharIndex = targetEndpointPath.indexOf("?");
        String rawPath = questionCharIndex < 0
                ? targetEndpointPath
                : targetEndpointPath.substring(0, questionCharIndex);

        String rawQueryParam = questionCharIndex < 0
                ? ""
                : targetEndpointPath.substring(questionCharIndex + 1);


        // Request 객체 생성.
        return new HttpRequest(
                httpVersion,
                httpHeader,
                httpBody,
                requestMethod,
                decodePath(rawPath),
                parseQueryParam(rawQueryParam),
                Map.of()
        );

    }

    private boolean fillBuffer() throws IOException {
        // 스트림에서 읽어서 버퍼에 기록.
        int readAmount = this.inputStream.read(
                this.buffer,
                this.end,
                this.buffer.length - this.end
        );

        if (readAmount < 0) {
            return false;
        }

        this.end += readAmount;
        return true;
    }

    private void compact() {
        // 버퍼의 값을 읽어내면 앞부분이 비게 되는데 이걸 이용해서 앞으로 다 땡겨줌
        int remaining = this.end - this.start;
        if (this.start > 0) {
            System.arraycopy(this.buffer, this.start, this.buffer, 0, remaining);
        }
        this.start = 0;
        this.end = remaining;
    }

    private int indexOfHeaderEnd(int findFrom) {
        // 헤더의 끝 (CRLFCRLF)를 찾자!
        for (int index = findFrom; index <= this.end - HEADER_END_BYTES.length; index++) {
            boolean isHeaderStart = Arrays.equals(
                    this.buffer, index, index + HEADER_END_BYTES.length,
                    HEADER_END_BYTES, 0, HEADER_END_BYTES.length
            );
            if (isHeaderStart) {
                return index;
            }
        }

        return -1;
    }

    private int readHeader() throws IOException {
        this.compact(); // buffer 앞으로 당기기

        int searchFromIndex = 0; // 헤더의 끝을 찾기 시작할 index

        // 지금까지의 버퍼를 뒤져서 찾은 Header의 끝부분 index,
        // 이후 반복해서 indexOfHeaderEnd와 fillBuffer를 호출하며 버퍼를 계속 채워가며 나올때 까지 찾음.
        int foundIndex;

        for (foundIndex = this.indexOfHeaderEnd(searchFromIndex);
             foundIndex < 0;
             foundIndex = this.indexOfHeaderEnd(searchFromIndex)
        ) {
            // 지금 읽고 있는 부분의 끝이 버퍼의 끝과 동일하다면,
            // 끝까지 읽었는데도 CRLFCRLF를 못찾은거임.
            if (this.end == this.buffer.length) {
                throw new HttpParseException(
                        HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE,
                        "request head exceeds " + this.buffer.length + " bytes"
                );
            }

            // 다음 시작 위치 계산
            searchFromIndex = Math.max(0, this.end - (HEADER_END_BYTES.length - 1));

            if(!this.fillBuffer()) {
                throw new EOFException("Connection closed in the middle of request header");
            }

        }

        return foundIndex + HEADER_END_BYTES.length;
    }

    private byte[] readBody(HttpHeader header) throws IOException {
        // Transfer-Encoding은 지원안할꺼임 수고.
        // 원래 RFC 9112에 따르면 Transfer-Encoding도 헤더에 같이 있을 수도 있지만,
        // 나는 지원 안할꺼임. 싫어요.
        if (header.contains("Transfer-Encoding")) {
            throw createParseException(HttpStatus.NOT_IMPLEMENTED, "Transfer-Encoding is not supported");
        }

        int contentLength = parseContentLength(header);
        byte[] bodyBytes = new byte[contentLength];

        // 버퍼에서 가져올 body의 길이를 계산 및 윈도우 이동.
        int bodyLengthInBuffer = Math.min(contentLength, this.end - this.start);
        System.arraycopy(this.buffer, this.start, bodyBytes, 0, bodyLengthInBuffer);
        this.start += bodyLengthInBuffer;

        // 스트림 내부에 남은걸 완전히 읽은 뒤 Content-Length 값과 비교
        int remainByteInStream = this.inputStream.readNBytes(
                bodyBytes, bodyLengthInBuffer, contentLength - bodyLengthInBuffer
        );

        // 스트림 내부에 남은 걸 읽어들여 길이를 계산 했을때, Content-Length 값을 넘지 못한다면, 중간에 끊긴 패킷
        if (bodyLengthInBuffer + remainByteInStream < contentLength) {
            throw new EOFException("Connection closed in the middle of request header");
        }

        return bodyBytes;
    }

    private static HttpHeader parseHeaders(String[] lines) {
        // 기본 헤더 구조를 사용.
        HttpHeader header = new DefaultHttpHeader();

        // 문자열로 파싱된 헤더 라인 전체를 개별로 파싱.
        for (String headerLine : lines) {
            // 헤더 라인의 field는 ':'으로 구분되며 이를 이용하여 파싱 시도.
            int colonIndex = headerLine.indexOf(':');
            // 이름 앞뒤 공백, 줄 접기(obs-fold)는 RFC 9112 5.1, 5.2에 따라 거절한다.
            if (colonIndex <= 0 || headerLine.startsWith(" ") || headerLine.startsWith("\t")
                    || Character.isWhitespace(headerLine.charAt(colonIndex - 1))) {
                throw createParseException(HttpStatus.BAD_REQUEST, "Malformed header line");
            }

            // RFC 규격을 통과한 헤더 라인을 헤더에 넣는다.
            try {
                header.add(headerLine.substring(0, colonIndex), headerLine.substring(colonIndex + 1).strip());

            } catch (IllegalArgumentException exception) {
                // 뭔가 헤더를 추가하는데 문제가 생긴다면?
                // 죽어버려욧!
                throw createParseException(HttpStatus.BAD_REQUEST, exception.getMessage());
            }
        }
        return header;
    }

    private static String[] parseRequestLine(String line) {
        String[] parts = line.split(" ", -1);

        // HTTP request 패킷의 헤더의 첫라인 형식 확인
        // <HttpMethod> <EndpointPath> <HttpVersion>
        // ex) GET /hello/im-endpoint HTTP/1.1
        // 만약 이 형태가 아닌 경우 잘못된 헤더 구조라고 판단하고 파싱 중지.
        if (parts.length != 3 || parts[0].isEmpty() || !parts[1].startsWith("/")) {

            throw createParseException(HttpStatus.BAD_REQUEST, "Malformed request line");
        }

        return parts;
    }

    private static int parseContentLength(HttpHeader header) {
        List<String> fieldValues = header.getAll("Content-Length");

        if (fieldValues.isEmpty()) {
            return 0;
        }

        // RFC 9112 6.3에 의거하여 Content-Length가 복수로 기재되었을때, 서로 다르다면 맞는걸 찾을 수 없다.
        if (fieldValues.stream().distinct().count() > 1) {
            throw createParseException(HttpStatus.BAD_REQUEST, "Conflicting field value: \"Content-Length\"");
        }

        // 숫자가 아닌 경우 Content-Length 파싱이 불가
        String fieldValue = fieldValues.getFirst();
        if (fieldValue.isEmpty()
                && !fieldValue.chars().allMatch(maybeDigit -> '0' <= maybeDigit && maybeDigit <= '9')) {
            throw createParseException(HttpStatus.BAD_REQUEST, "Invalid field value: \"Content-Length\"");
        }

        // 1차 검증 통과된 field value로 Content-Length 파싱 시도
        long contentLength;
        try {
            contentLength = Long.parseLong(fieldValue);
        } catch (NumberFormatException numberFormatException) {
            contentLength = Long.MAX_VALUE;
        }

        // body 길이 상한을 넘는 Content-Length인 경우 지원 불가.
        if (contentLength > MAX_BODY_BYTES) {
            throw createParseException(HttpStatus.PAYLOAD_TOO_LARGE, "body exceeds " + MAX_BODY_BYTES + " bytes");
        }

        return (int) contentLength;
    }

    private static String decodePath(String rawPath) {
        try {
            return URLDecoder.decode(rawPath.replace("+", "%20B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw createParseException(HttpStatus.BAD_REQUEST, "Invalid percent-encoding in path");
        }
    }

    private static Map<String, List<String>> parseQueryParam(String rawQueryParam) {
        Map<String, List<String>> queryParam = new LinkedHashMap<>();

        // QSP가 없는 경우.
        if (rawQueryParam.isEmpty()) {
            return queryParam;
        }

        // QSP 파싱 시도
        try {
            // QSP 구분자 '&'을 기준으로 분리
            for (String queryPair : rawQueryParam.split("&")) {
                if (queryPair.isEmpty()) {
                    continue;
                }

                // QSP 한 묶음을 '='을 기준으로 Key/Value로 분리
                int equalChar = queryPair.indexOf('=');
                String key = URLDecoder.decode(
                        equalChar < 0
                                ? queryPair
                                : queryPair.substring(0, equalChar),
                        StandardCharsets.UTF_8
                );

                String value = equalChar < 0
                        ? ""
                        : URLDecoder.decode(queryPair.substring(equalChar + 1), StandardCharsets.UTF_8);

                // Map.computeIfAbsent로 key가 없는 경우에만 값을 추가.
                queryParam.computeIfAbsent(key, _ -> new ArrayList<>()).add(value);
            }
        } catch (IllegalArgumentException e) {
            throw createParseException(HttpStatus.BAD_REQUEST, "Invalid percent-encoding in query");
        }
        return queryParam;
    }

    private static HttpParseException createParseException(HttpStatus status, String message) {
        return new HttpParseException(status, message);
    }
}

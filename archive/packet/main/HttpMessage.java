package com.minihttp.packet;

import com.minihttp.packet.body.HttpBody;
import com.minihttp.packet.header.HttpHeader;
import com.minihttp.packet.protocol.HttpVersion;

public sealed interface HttpMessage permits HttpRequest, HttpResponse {
    HttpVersion version();

    HttpHeader header();

    HttpBody body();
}

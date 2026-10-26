package com.minihttp.config;

import com.minihttp.packet.protocol.HttpVersion;

public record ConnectionConfig(int maxRequestByConnection,
                               int keepAliveTimeout,
                               HttpVersion connectionVersion
                               ) {
}

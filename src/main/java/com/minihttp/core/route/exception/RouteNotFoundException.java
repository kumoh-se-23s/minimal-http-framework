package com.minihttp.core.route.exception;

import com.minihttp.packet.protocol.HttpStatus;

public class RouteNotFoundException extends HttpStatusException {
    public RouteNotFoundException(String path) {
        super(HttpStatus.NOT_FOUND, "No route for " + path);
    }
}

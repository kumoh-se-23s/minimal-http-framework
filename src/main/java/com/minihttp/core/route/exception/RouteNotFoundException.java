package com.minihttp.core.route.exception;

import com.minihttp.packet.protocol.HttpStatus;

public class RouteNotFoundException extends HttpStatusException {
    // FastAPI와 같은 응답: 404 {"detail":"Not Found"}
    public RouteNotFoundException() {
        super(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reason());
    }
}

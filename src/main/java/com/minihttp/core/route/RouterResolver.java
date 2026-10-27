package com.minihttp.core.route;

import com.minihttp.core.route.binding.Endpoint;
import com.minihttp.core.route.binding.EndpointScanner;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RouterResolver {
    private static Map<BaseRouter, EndpointScanner> SCANNER_MAP = new HashMap<>();

    private RouterResolver(BaseRouter router) {
        this.setScannerMap(router);
    }

    private void setScannerMap(BaseRouter router) {
        SCANNER_MAP.put(router, EndpointScanner.create(router.getClass()));
    }
}

package com.minihttp;

import com.minihttp.config.ServerConfig;
import com.minihttp.router.ExampleRouter;

public class Main {
    public static void main(String[] args) throws Exception {
        MiniHttp.create()
                .includeRouter(new ExampleRouter())
                .run(ServerConfig.builder().listen(8080).build());
    }
}

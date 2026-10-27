package com.minihttp.router;

import com.minihttp.core.routing.BaseRouter;
import com.minihttp.core.routing.annotation.Get;
import com.minihttp.core.routing.annotation.Query;
import com.minihttp.packet.HttpResponse;
import lombok.NonNull;

public class ExampleRouter extends BaseRouter {
    public ExampleRouter() {
        super("/example");
    }

    @Get("/test")
    public String testRequestEndpoint(){
        return "되냐?";
    }

    @Get("/test")
    public String testQSP(@Query String name) {
        return "내 이름은 " + name + "이지롱";
    }

}

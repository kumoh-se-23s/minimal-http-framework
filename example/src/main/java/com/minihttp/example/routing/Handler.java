package com.minihttp.example.routing;

import com.minihttp.example.http.HttpRequest;
import com.minihttp.example.http.HttpResponse;

@FunctionalInterface
public interface Handler {
    HttpResponse handle(HttpRequest request) throws Exception;
}

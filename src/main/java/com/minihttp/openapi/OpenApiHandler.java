package com.minihttp.openapi;

import com.minihttp.config.OpenApiConfig;
import com.minihttp.core.handler.Handler;
import com.minihttp.json.JsonWriter;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OpenApiHandler implements Handler {
    private record StaticResource(byte[] bytes, ContentType contentType) {
    }

    private static final String RESOURCE_DIRECTORY = "/com/minihttp/openapi/swagger-ui/";
    private static final String INDEX_TEMPLATE = "index.html";
    private static final Map<String, ContentType> ASSETS = Map.of(
            "swagger-ui-bundle.js", ContentType.TEXT_JAVASCRIPT,
            "swagger-ui.css", ContentType.TEXT_CSS,
            "favicon-32x32.png", ContentType.IMAGE_PNG,
            "favicon-16x16.png", ContentType.IMAGE_PNG
    );

    private final Handler next;
    private final Map<String, StaticResource> resources;

    private OpenApiHandler(Handler next, OpenApiConfig config, String specJson) {
        this.next = next;

        Map<String, StaticResource> resources = new HashMap<>();
        resources.put(config.specPath(),
                      new StaticResource(specJson.getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON));
        resources.put(config.docsPath(),
                      new StaticResource(OpenApiHandler.renderIndex(config), ContentType.TEXT_HTML));

        String assetPath = OpenApiHandler.assetPathOf(config);
        ASSETS.forEach((assetName, contentType) -> resources.put(
                assetPath + "/" + assetName,
                new StaticResource(OpenApiHandler.readResource(assetName), contentType)));

        this.resources = Map.copyOf(resources);
    }

    public static OpenApiHandler wrap(@NonNull Handler next, @NonNull OpenApiConfig config, @NonNull String specJson) {
        return new OpenApiHandler(next, config, specJson);
    }

    public static List<String> reservedPaths(@NonNull OpenApiConfig config) {
        List<String> reservedPaths = new ArrayList<>();
        reservedPaths.add(config.specPath());
        reservedPaths.add(config.docsPath());

        String assetPath = OpenApiHandler.assetPathOf(config);
        for (String assetName : ASSETS.keySet()) {
            reservedPaths.add(assetPath + "/" + assetName);
        }

        return List.copyOf(reservedPaths);
    }

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        StaticResource resource = this.resources.get(request.path());

        if (request.method() != HttpMethod.GET || resource == null) {
            return this.next.handle(request);
        }

        HttpResponse response = new HttpResponse(HttpStatus.OK);
        response.body().set(resource.bytes());
        response.contentType(resource.contentType());
        return response;
    }

    private static String assetPathOf(OpenApiConfig config) {
        return config.docsPath().equals("/") ? "" : config.docsPath();
    }

    private static byte[] renderIndex(OpenApiConfig config) {
        String template = new String(OpenApiHandler.readResource(INDEX_TEMPLATE), StandardCharsets.UTF_8);
        String html = template.replace("{{title}}", OpenApiHandler.escapeHtml(config.title()))
                              .replace("{{assetPath}}", OpenApiHandler.escapeHtml(OpenApiHandler.assetPathOf(config)))
                              .replace("{{specUrl}}", JsonWriter.write(config.specPath()).replace("<", "\\u003c"));
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readResource(String resourceName) {
        try (InputStream inputStream = OpenApiHandler.class.getResourceAsStream(RESOURCE_DIRECTORY + resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing Swagger UI resource: " + RESOURCE_DIRECTORY + resourceName);
            }

            return inputStream.readAllBytes();

        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Swagger UI resource: " + resourceName, exception);
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}

package com.minihttp;

import com.minihttp.config.ServerConfig;
import com.minihttp.core.HttpServer;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import com.minihttp.packet.structure.HttpHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniHttpInjectionTest {
    static final class CloseLog {
        private final List<String> closedNames = new ArrayList<>();

        void record(String name) {
            this.closedNames.add(name);
        }

        List<String> closedNames() {
            return this.closedNames;
        }
    }

    interface ItemRepository {
        List<String> findAll();
    }

    static final class InMemoryItemRepository implements ItemRepository, AutoCloseable {
        private final CloseLog closeLog;

        InMemoryItemRepository(CloseLog closeLog) {
            this.closeLog = closeLog;
        }

        @Override
        public List<String> findAll() {
            return List.of("apple", "banana");
        }

        @Override
        public void close() {
            this.closeLog.record("repository");
        }
    }

    static final class ItemService implements AutoCloseable {
        private final ItemRepository itemRepository;
        private final CloseLog closeLog;

        ItemService(ItemRepository itemRepository, CloseLog closeLog) {
            this.itemRepository = itemRepository;
            this.closeLog = closeLog;
        }

        List<String> names() {
            return this.itemRepository.findAll();
        }

        @Override
        public void close() {
            this.closeLog.record("itemService");
        }
    }

    public static final class ItemRouter extends BaseRouter {
        private final ItemService itemService;

        public ItemRouter(ItemService itemService) {
            super("/items");
            this.itemService = itemService;
        }

        @Get
        public List<String> list() {
            return this.itemService.names();
        }
    }

    public static final class HealthRouter extends BaseRouter {
        @Get("/health")
        public String health() {
            return "ok";
        }
    }

    static class TokenParser {
        Long parse(String authorization) {
            return Long.parseLong(authorization.substring("Bearer user-".length()));
        }
    }

    static final class CurrentUserId implements Dependency<Long> {
        private final TokenParser tokenParser;

        CurrentUserId(TokenParser tokenParser) {
            this.tokenParser = tokenParser;
        }

        @Override
        public Long resolve(HttpRequest request) {
            return this.tokenParser.parse(request.header().get("Authorization").orElseThrow());
        }
    }

    public static final class MeRouter extends BaseRouter {
        @Get("/me")
        public Long me(@Depends(CurrentUserId.class) Long userId) {
            return userId;
        }
    }

    public static final class UnboundRouter extends BaseRouter {
        public UnboundRouter(ItemRepository itemRepository) {
        }
    }

    private static MiniHttp app() {
        return MiniHttp.create()
                       .provide(CloseLog.class, new CloseLog())
                       .bind(ItemRepository.class, InMemoryItemRepository.class);
    }

    private static HttpRequest request(String path, Map<String, String> headers) {
        HttpHeader header = new DefaultHttpHeader();
        header.set(HttpHeaderField.HOST, "localhost");
        headers.forEach(header::set);

        return new HttpRequest(HttpVersion.HTTP_1_1, header, new DefaultHttpBody(new byte[0]),
                               HttpMethod.GET, path, Map.of(), Map.of());
    }

    private static String bodyOf(HttpResponse response) {
        return response.body().asString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("라우터 주입")
    class RouterInjectionTest {
        @Test
        void createsRouterWithServiceAndRepository() throws Exception {
            Handler handler = MiniHttpInjectionTest.app().includeRouter(ItemRouter.class).buildHandler();

            HttpResponse response = handler.handle(MiniHttpInjectionTest.request("/items", Map.of()));

            assertEquals(HttpStatus.OK, response.status());
            assertEquals("[\"apple\",\"banana\"]", MiniHttpInjectionTest.bodyOf(response));
        }

        @Test
        void keepsInstanceRoutersAlongsideClassRouters() throws Exception {
            Handler handler = MiniHttpInjectionTest.app()
                                                   .includeRouter(new HealthRouter())
                                                   .includeRouter(ItemRouter.class)
                                                   .buildHandler();

            assertEquals("\"ok\"", MiniHttpInjectionTest.bodyOf(handler.handle(MiniHttpInjectionTest.request("/health", Map.of()))));
            assertEquals(HttpStatus.OK, handler.handle(MiniHttpInjectionTest.request("/items", Map.of())).status());
        }

        @Test
        void listsClassRouterEndpointsInOpenApi() throws Exception {
            Handler handler = MiniHttpInjectionTest.app().includeRouter(ItemRouter.class).buildHandler();

            String spec = MiniHttpInjectionTest.bodyOf(handler.handle(MiniHttpInjectionTest.request("/openapi.json", Map.of())));

            assertTrue(spec.contains("\"/items\""));
        }

        @Test
        void failsAtStartupWhenGraphIsIncomplete() {
            MiniHttp app = MiniHttp.create().includeRouter(UnboundRouter.class);

            IllegalStateException exception = assertThrows(IllegalStateException.class, app::buildHandler);

            assertTrue(exception.getMessage().contains(ItemRepository.class.getName()));
        }
    }

    @Nested
    @DisplayName("Dependency 주입")
    class DependencyInjectionTest {
        @Test
        void buildsUnregisteredDependencyWithConstructorInjection() throws Exception {
            Handler handler = MiniHttp.create().includeRouter(new MeRouter()).buildHandler();

            HttpResponse response = handler.handle(MiniHttpInjectionTest.request("/me", Map.of("Authorization", "Bearer user-7")));

            assertEquals("7", MiniHttpInjectionTest.bodyOf(response));
        }

        @Test
        void prefersRegisteredDependencyInstance() throws Exception {
            Handler handler = MiniHttp.create()
                                      .dependency(new CurrentUserId(new TokenParser() {
                                          @Override
                                          Long parse(String authorization) {
                                              return 99L;
                                          }
                                      }))
                                      .includeRouter(new MeRouter())
                                      .buildHandler();

            HttpResponse response = handler.handle(MiniHttpInjectionTest.request("/me", Map.of("Authorization", "Bearer user-7")));

            assertEquals("99", MiniHttpInjectionTest.bodyOf(response));
        }
    }

    @Nested
    @DisplayName("종료")
    @Timeout(10)
    class LifecycleTest {
        @Test
        void closesContainerAfterServerStops() throws Exception {
            CloseLog closeLog = new CloseLog();
            MiniHttp app = MiniHttp.create()
                                   .provide(CloseLog.class, closeLog)
                                   .bind(ItemRepository.class, InMemoryItemRepository.class)
                                   .includeRouter(ItemRouter.class);
            ServerConfig config = ServerConfig.builder()
                                              .listen(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                                              .build();

            HttpServer server = app.start(config);
            assertTrue(closeLog.closedNames().isEmpty());

            server.stop();

            assertEquals(List.of("itemService", "repository"), closeLog.closedNames());
        }
    }
}

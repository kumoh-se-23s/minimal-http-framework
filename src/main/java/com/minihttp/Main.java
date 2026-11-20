package com.minihttp;

import com.minihttp.config.ServerConfig;
import com.minihttp.core.HttpServer;
import com.minihttp.infra.AppSettings;
import com.minihttp.infra.ConsoleNotificationSender;
import com.minihttp.infra.IdGenerator;
import com.minihttp.infra.NotificationSender;
import com.minihttp.infra.SequenceIdGenerator;
import com.minihttp.log.MiniHttpLogging;
import com.minihttp.repository.InMemoryMemoRepository;
import com.minihttp.repository.InMemoryOrderRepository;
import com.minihttp.repository.MemoRepository;
import com.minihttp.repository.OrderRepository;
import com.minihttp.router.ExampleRouter;
import com.minihttp.router.MemoRouter;
import com.minihttp.router.OrderRouter;
import com.minihttp.service.BulkDiscountPriceCalculator;
import com.minihttp.service.PriceCalculator;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Main {
    private static final System.Logger LOGGER = MiniHttpLogging.logger("minihttp.example");

    public static void main(String[] args) throws Exception {
        MiniHttpLogging.enableConsole();
        ExecutorService notificationExecutor = Executors.newVirtualThreadPerTaskExecutor();

        HttpServer server = MiniHttp.create()
                                    .bind(MemoRepository.class, InMemoryMemoRepository.class)
                                    .bind(OrderRepository.class, InMemoryOrderRepository.class)
                                    .bind(NotificationSender.class, ConsoleNotificationSender.class)
                                    .bind(PriceCalculator.class, BulkDiscountPriceCalculator.class)
                                    .provide(Clock.class, Clock.systemDefaultZone())
                                    .provide(AppSettings.class, new AppSettings("mini-shop", 2, "secret-key"))
                                    .provide(IdGenerator.class, new SequenceIdGenerator(1000))
                                    .provide(ExecutorService.class, notificationExecutor)
                                    .includeRouter(new ExampleRouter())
                                    .includeRouter(MemoRouter.class)
                                    .includeRouter(OrderRouter.class)
                                    .start(ServerConfig.builder().listen(8080).build());

        server.onStopped(() -> Main.shutdownNotificationExecutor(notificationExecutor));
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> Main.stopServer(server)));
    }

    private static void shutdownNotificationExecutor(ExecutorService notificationExecutor) {
        LOGGER.log(System.Logger.Level.INFO,
                   "Notification executor after container close: isShutdown=" + notificationExecutor.isShutdown()
                           + ", shutting it down from Main");
        notificationExecutor.close();
    }

    private static void stopServer(HttpServer server) {
        try {
            server.stop();

        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}



// Optional<List<Integer>>
// 안넣었다. -> Optional.empty().
// 넣었다 -> Optional.of(List<Integer>)


// QSP 엔드포인트
// 1. key, value 모두 존재 -> 정상 케이스
// 2. key는 존재하지만 value는 없음 : /some-endpoint?key= -> 예외 => 422
// 3. key, value 모두 없음
//      1. Optional<String> -> Optional.empty()
//      2. Optional<List<String>> -> Optional.empty()
//      3. Optional<Integer> -> Optional.empty()
//      4. Optional<List<Integer>> -> Optional.empty()
//      5. String -> 422
//      6. List<String> -> 422
//      7. Integer -> 422
//      8. List<Integer> -> 422

// 1. key/value가 정의 여부
// 2. Optional 파라미터는 Nullable한것

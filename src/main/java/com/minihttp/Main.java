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

    /*
     * 생성자 주입 컨테이너 사용 예시
     *
     * 컨테이너는 클래스의 생성자를 보고 파라미터 타입을 재귀적으로 만들어 넣는다. (타입당 인스턴스 하나, 싱글턴)
     *   OrderRouter(OrderService, AuditLog, AppSettings)
     *     └ OrderService(OrderRepository, IdGenerator, PriceCalculator, NotificationSender, AuditLog, AppSettings, Clock)
     *         └ ConsoleNotificationSender(Clock, AppSettings, ExecutorService)
     *
     * 생성자 선택 규칙: 생성자가 하나면 그것, 여러 개면 public 생성자가 하나일 때 그것, 아니면 시작 시 에러.
     * 바인딩 없는 인터페이스, 순환 참조, 모호한 생성자는 모두 서버 시작 시점에 에러로 잡힌다.
     *
     * 실행 후 확인해 볼 요청
     *   curl localhost:8080/orders/info
     *   curl -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"item":"pen","quantity":12,"unitPrice":1000}'
     *   curl 'localhost:8080/orders?page=1'
     *   curl localhost:8080/orders/audit                              → 401 (API 키 없음)
     *   curl localhost:8080/orders/audit -H 'X-Api-Key: secret-key'   → 200
     *   브라우저: http://localhost:8080/docs
     */
    public static void main(String[] args) throws Exception {
        MiniHttpLogging.enableConsole();

        // 컨테이너 밖에서 직접 만든 자원. 아래에서 provide로 넣기 때문에 컨테이너는 이걸 닫지 않는다. (만든 쪽인 Main이 닫는다)
        ExecutorService notificationExecutor = Executors.newVirtualThreadPerTaskExecutor();

        HttpServer server = MiniHttp.create()
                                    // ── bind: 추상 타입(인터페이스/추상 클래스)을 요청받으면 어떤 구현체를 만들지 알려준다.
                                    // 인터페이스 → 구현체. 가장 기본적인 형태.
                                    .bind(MemoRepository.class, InMemoryMemoRepository.class)
                                    .bind(OrderRepository.class, InMemoryOrderRepository.class)
                                    // 구현체가 다시 다른 의존성(Clock, AppSettings, ExecutorService)을 생성자로 받는 경우. 그것들도 컨테이너가 채운다.
                                    .bind(NotificationSender.class, ConsoleNotificationSender.class)
                                    // 추상 클래스 → 구체 클래스. 인터페이스와 똑같이 동작한다. (10개 이상 주문 시 10% 할인)
                                    .bind(PriceCalculator.class, BulkDiscountPriceCalculator.class)

                                    // ── provide: 컨테이너가 생성자로 만들 수 없는 객체를 이미 만든 인스턴스로 넣는다.
                                    // JDK 타입. String, Clock 같은 JDK 타입은 provide 없이는 만들 수 없다.
                                    .provide(Clock.class, Clock.systemDefaultZone())
                                    // 설정 record. 타입당 인스턴스가 하나라서 String/int 값 여러 개를 직접 주입할 수 없으니 record 하나로 묶어서 넣는다.
                                    // OrderService, OrderRouter, ConsoleNotificationSender, ApiKeyAuthentication이 모두 이 인스턴스를 받는다.
                                    .provide(AppSettings.class, new AppSettings("mini-shop", 2, "secret-key"))
                                    // 생성자에 주입할 수 없는 인자(시작 번호 1000)가 필요한 객체는 직접 만들어서 인터페이스 타입으로 넣는다.
                                    .provide(IdGenerator.class, new SequenceIdGenerator(1000))
                                    // AutoCloseable이지만 provide로 넣었으니 서버 종료 시 컨테이너가 닫지 않는다.
                                    // 반대로 AuditLog는 컨테이너가 직접 만든 AutoCloseable이라 종료 시 컨테이너가 닫는다.
                                    .provide(ExecutorService.class, notificationExecutor)

                                    // ── 라우터 등록
                                    // 인스턴스로 등록: 컨테이너가 건드리지 않는다. 의존성이 없는 라우터나 테스트에서 가짜 객체를 넣을 때 쓴다.
                                    .includeRouter(new ExampleRouter())
                                    // 클래스로 등록: 컨테이너가 생성자를 보고 서비스/리포지토리를 채워서 만든다.
                                    .includeRouter(MemoRouter.class)
                                    // 여러 서비스를 받는 라우터. /orders/audit의 @Depends(ApiKeyAuthentication.class)도
                                    // dependency(...)로 등록하지 않았지만 컨테이너가 AppSettings를 넣어서 만든다.
                                    .includeRouter(OrderRouter.class)
                                    .start(ServerConfig.builder().listen(8080).build());

        // 서버가 처리 중인 연결을 다 정리하고 컨테이너가 자기 객체(AuditLog 등)를 닫은 뒤에 실행된다.
        // provide로 넣은 자원은 여기서 직접 닫는다.
        server.onStopped(() -> Main.shutdownNotificationExecutor(notificationExecutor));

        // Ctrl+C / SIGTERM 시 서버를 멈춘다. run() 대신 start()를 쓰면 종료 처리를 직접 해줘야 한다.
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

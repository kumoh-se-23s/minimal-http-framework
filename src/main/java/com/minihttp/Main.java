package com.minihttp;

import com.minihttp.config.ServerConfig;
import com.minihttp.controller.external.AuthRouter;
import com.minihttp.controller.external.ProjectLikeRouter;
import com.minihttp.controller.external.ProjectRouter;
import com.minihttp.controller.internal.AdminProjectRouter;
import com.minihttp.core.HttpServer;
import com.minihttp.infra.AppSettings;
import com.minihttp.infra.ConnectionPool;
import com.minihttp.infra.ConsoleNotificationSender;
import com.minihttp.infra.NotificationSender;
import com.minihttp.log.MiniHttpLogging;
import com.minihttp.router.ExampleRouter;
import com.minihttp.service.LikeCountRanking;
import com.minihttp.service.ProjectRanking;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/*
 * ======================================================================================
 *  생성자 주입 컨테이너 예시 (fusion-project 구조를 그대로 본뜸)
 * ======================================================================================
 *
 *  예시 클래스                                   ↔  fusion-project
 *  ------------------------------------------------------------------------------------
 *  controller.external.ProjectRouter            ↔  controller.external.ProjectRouter
 *  controller.external.ProjectLikeRouter        ↔  controller.external.ProjectLikeRouter
 *  controller.internal.AdminProjectRouter       ↔  controller.internal.AdminProjectRouter
 *  service.external.ProjectService              ↔  service.external.ProjectQueryService 등
 *  service.internal.ProjectService              ↔  service.internal.AdminProjectControlService 등
 *     (이름이 같은 클래스가 패키지만 다르게 두 개 → external/internal RewardService와 같은 상황. 컨테이너는 클래스로 구분하므로 문제 없음)
 *  repository.ProjectRepository                 ↔  repository.ProjectRepository
 *  repository.ProjectLikeRepository             ↔  repository.ProjectLikeRepository
 *  repository.InMemoryRepository<T>             ↔  repository.BaseRepositoryImpl<T, ID>
 *  auth.CurrentUser                             ↔  auth.CurrentUser
 *  auth.TokenProvider                           ↔  util.JwtTokenProvider
 *  infra.ConnectionPool                         ↔  config.PooledDataSource (BasicDataSource)
 *  infra.AppSettings                            ↔  config.PropertieData 값들
 *
 *  의존 관계 (전부 컨테이너가 생성자를 보고 자동으로 채운다)
 *    ProjectRouter(external.ProjectService)
 *      └ external.ProjectService(ProjectRepository, ProjectLikeRepository, ProjectRanking, NotificationSender, AppSettings, Clock)
 *          ├ ProjectRepository()                         ← 구체 클래스 + 기본 생성자 → 등록 없이 그냥 만들어진다
 *          ├ ProjectRanking → LikeCountRanking(ProjectLikeRepository)   ← bind (추상 클래스)
 *          └ NotificationSender → ConsoleNotificationSender(Clock, AppSettings)   ← bind (인터페이스)
 *    AdminProjectRouter(internal.ProjectService, ConnectionPool)
 *      └ internal.ProjectService(ProjectRepository, ProjectLikeRepository, AuditLog)
 *    CurrentUser(TokenProvider)  ← @Depends(CurrentUser.class)로 쓰이는 Dependency도 컨테이너가 만든다
 *      └ TokenProvider(AppSettings)
 *
 *  규칙
 *    - 생성자가 하나면 그것, 여러 개면 public 생성자가 하나일 때 그것을 쓴다. (Lombok @RequiredArgsConstructor 그대로 OK)
 *    - 타입당 인스턴스 하나(싱글턴). ProjectRepository는 두 서비스가 같은 인스턴스를 받는다.
 *    - 바인딩 없는 인터페이스, 순환 참조, 모호한 생성자는 서버 시작 시점에 에러로 잡힌다.
 *    - 생성자만 호출하므로 BaseService의 `protected final TransactionTemplate tx = new TransactionTemplate();`
 *      같은 필드 초기화는 지금처럼 그대로 동작한다.
 *
 *  실행 후 확인해 볼 요청
 *    TOKEN=$(curl -s -X POST localhost:8080/auth/token -H 'Content-Type: application/json' -d '{"userId":7}' | cut -d'"' -f4)
 *    curl -X POST localhost:8080/project -H 'Content-Type: application/json' -d '{"title":"첫 프로젝트"}'    → 401 (토큰 없음)
 *    curl -X POST localhost:8080/project -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"title":"첫 프로젝트"}'   → 201
 *    curl -X POST localhost:8080/project/like/1 -H "Authorization: Bearer $TOKEN"     → 201
 *    curl localhost:8080/project/1
 *    curl localhost:8080/project/popular
 *    curl localhost:8080/admin/project/pool -H "Authorization: Bearer $TOKEN"
 *    브라우저: http://localhost:8080/docs
 *
 * ======================================================================================
 *  fusion-project 적용 방법
 * ======================================================================================
 *
 *  1) AppConfig 삭제
 *     지금 AppConfig는 `new XRepository()`, `new XService(repo, repo, ...)`를 손으로 조립한다.
 *     리포지토리는 전부 기본 생성자가 있는 구체 클래스, 서비스/라우터는 생성자로 받기만 하므로
 *     AppConfig의 필드는 전부 지우고 라우터만 includeRouter(XRouter.class)로 등록하면 된다.
 *     bind는 인터페이스/추상 클래스를 생성자로 받는 곳이 생길 때만 필요하다. (지금 fusion에는 없음)
 *     AppConfig 생성자에 있던 SchemaGenerator 실행은 Main의 서버 시작 전으로 옮긴다.
 *
 *  2) CurrentUser
 *     필드에서 `new JwtTokenProvider()` 하던 것을 생성자로 받게 바꾼다. dependency(...) 등록은 필요 없다.
 *       public class CurrentUser implements Dependency<Long> {
 *           private final JwtTokenProvider tokenProvider;
 *           public CurrentUser(JwtTokenProvider tokenProvider) { this.tokenProvider = tokenProvider; }
 *           ...
 *       }
 *     JwtTokenProvider는 기본 생성자가 있으니 그대로 컨테이너가 만든다. (비밀 키를 설정으로 빼고 싶으면 생성자로 설정 record를 받게 하면 됨)
 *
 *  3) DataSource
 *     PooledDataSource.getDataSource()로 만든 커넥션 풀은 provide로 넣는다.
 *     provide로 넣은 객체는 컨테이너가 닫지 않으므로 onStopped에서 직접 닫는다. (BasicDataSource는 AutoCloseable)
 *     지금처럼 리포지토리가 정적 메서드로 커넥션을 얻는 구조라면 provide 없이 종료 시 닫기만 해도 된다.
 *
 *  4) fusion-project Main 예시
 *       public class Main {
 *           public static void main(String[] args) throws Exception {
 *               SchemaGenerator.Mode ddlMode = SchemaGenerator.Mode.from(PropertieData.get("db.ddl-auto", "none"));
 *               new SchemaGenerator().execute(ddlMode, "model.domain");
 *
 *               DataSource dataSource = PooledDataSource.getDataSource();
 *
 *               HttpServer server = MiniHttp.create()
 *                       .provide(DataSource.class, dataSource)
 *                       .includeRouter(ProjectRouter.class)
 *                       .includeRouter(ProjectLikeRouter.class)
 *                       .includeRouter(ReviewRouter.class)
 *                       .includeRouter(ReviewLikeRouter.class)
 *                       .includeRouter(CategoryRouter.class)
 *                       .includeRouter(AdminProjectRouter.class)
 *                       .includeRouter(AdminCategoryRouter.class)
 *                       .includeRouter(AdminReviewRouter.class)
 *                       .includeRouter(AdminDenyProjectRouter.class)
 *                       .start(ServerConfig.builder().listen(8080).build());
 *
 *               server.onStopped(() -> ((BasicDataSource) dataSource).close());
 *               Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
 *                   try {
 *                       server.stop();
 *                   } catch (InterruptedException interrupted) {
 *                       Thread.currentThread().interrupt();
 *                   }
 *               }));
 *           }
 *       }
 *     BasicDataSource.close()는 SQLException을 던지므로 실제로는 try/catch로 감싼다.
 */
public class Main {
    private static final System.Logger LOGGER = MiniHttpLogging.logger("minihttp.example");

    public static void main(String[] args) throws Exception {
        MiniHttpLogging.enableConsole();

        // 컨테이너 밖에서 만든 자원 (fusion의 PooledDataSource.getDataSource() 자리).
        // provide로 넣기 때문에 컨테이너는 이걸 닫지 않는다. 만든 쪽인 Main이 onStopped에서 닫는다.
        ConnectionPool connectionPool = ConnectionPool.open("jdbc:mysql://localhost:3306/fusion", 10);

        HttpServer server = MiniHttp.create()
                                    // ── provide: 컨테이너가 생성자로 만들 수 없는 객체를 이미 만든 인스턴스로 넣는다.
                                    // 외부에서 만든 커넥션 풀. AdminProjectRouter가 생성자로 받는다.
                                    .provide(ConnectionPool.class, connectionPool)
                                    // 설정 record. 타입당 인스턴스가 하나라서 String/int 값 여러 개를 직접 주입할 수 없으니 record 하나로 묶는다.
                                    // (fusion에서는 PropertieData 값들을 이런 record로 묶어서 넣으면 된다)
                                    .provide(AppSettings.class, new AppSettings("fusion-example", 2, "change-me-secret"))
                                    // JDK 타입은 컨테이너가 만들 수 없으니 provide로 넣는다.
                                    .provide(Clock.class, Clock.systemDefaultZone())

                                    // ── bind: 생성자 파라미터가 인터페이스/추상 클래스일 때만 필요하다.
                                    // ProjectRepository처럼 기본 생성자가 있는 구체 클래스는 bind 없이 자동으로 만들어진다.
                                    // 인터페이스 → 구현체. 구현체(ConsoleNotificationSender)가 받는 Clock, AppSettings도 컨테이너가 채운다.
                                    .bind(NotificationSender.class, ConsoleNotificationSender.class)
                                    // 추상 클래스 → 구체 클래스. 구현체(LikeCountRanking)가 받는 ProjectLikeRepository도 컨테이너가 채운다.
                                    .bind(ProjectRanking.class, LikeCountRanking.class)

                                    // ── 라우터 등록
                                    // 인스턴스로 등록: 컨테이너가 건드리지 않는다. 의존성이 없는 라우터나 테스트에서 가짜 객체를 넣을 때 쓴다.
                                    .includeRouter(new ExampleRouter())
                                    // 클래스로 등록: 컨테이너가 생성자를 보고 서비스 → 리포지토리까지 채워서 만든다. (fusion은 전부 이 방식)
                                    .includeRouter(AuthRouter.class)
                                    .includeRouter(ProjectRouter.class)
                                    .includeRouter(ProjectLikeRouter.class)
                                    .includeRouter(AdminProjectRouter.class)
                                    .start(ServerConfig.builder().listen(8080).build());

        // 서버가 처리 중인 연결을 다 정리하고, 컨테이너가 자기가 만든 AutoCloseable(AuditLog)을 닫은 뒤에 실행된다.
        // provide로 넣은 자원은 여기서 직접 닫는다.
        server.onStopped(() -> Main.closeConnectionPool(connectionPool));

        // Ctrl+C / SIGTERM 시 서버를 멈춘다. run() 대신 start()를 쓰면 종료 처리를 직접 달아야 한다.
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> Main.stopServer(server)));
    }

    private static void closeConnectionPool(ConnectionPool connectionPool) {
        LOGGER.log(System.Logger.Level.INFO,
                   "ConnectionPool after container close: closed=" + connectionPool.status().closed() + ", closing it from Main");
        connectionPool.close();
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

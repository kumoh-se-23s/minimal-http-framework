package com.minihttp;

import com.minihttp.config.ServerConfig;
import com.minihttp.router.ExampleRouter;

import java.util.Optional;

public class Main {
    public static void main(String[] args) throws Exception {
        MiniHttp.create()
                .includeRouter(new ExampleRouter())
                .run(ServerConfig.builder().listen(8080).build());
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

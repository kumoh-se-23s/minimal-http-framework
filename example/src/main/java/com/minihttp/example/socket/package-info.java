/**
 * HTTP 서버 코어를 만들기 전에 소켓 통신만 따로 떼어 본 예제 모음.
 *
 * <p>각 클래스는 main 하나로 서버와 클라이언트를 같은 프로세스에서 띄우고 결과를 출력한다.
 * main은 "1) 2) 3)..." 단계로 읽히도록 썼고, 단계 하나가 메서드 하나다.
 *
 * <p>추천 읽는 순서 (클래스 이름의 StepNN 번호와 같다):
 * <ol start="0">
 *   <li>{@link com.minihttp.example.socket.Step00SocketBasicsExample} — Socket/ServerSocket API 기본: 상태, 주소, 스트림, close, 연결 거절</li>
 *   <li>{@link com.minihttp.example.socket.Step01BlockingEchoExample} — bind → accept → read/write → close 기본 흐름</li>
 *   <li>{@link com.minihttp.example.socket.Step02HalfCloseExample} — shutdownOutput과 EOF, close와의 차이</li>
 *   <li>{@link com.minihttp.example.socket.Step03StreamFramingExample} — TCP엔 메시지 경계가 없다, 헤더 끝 찾기와 남는 바이트</li>
 *   <li>{@link com.minihttp.example.socket.Step04TimeoutExample} — accept / connect / read 타임아웃</li>
 *   <li>{@link com.minihttp.example.socket.Step05SocketOptionsExample} — TCP_NODELAY, SO_KEEPALIVE, 버퍼 크기 등 옵션</li>
 *   <li>{@link com.minihttp.example.socket.Step06ThreadPerConnectionExample} — 연결마다 가상 스레드, 줄 단위 프로토콜</li>
 *   <li>{@link com.minihttp.example.socket.Step07KeepAliveExample} — 한 연결로 여러 요청: 요청 루프, 연결 유지/종료 판단, 파이프라이닝, 유휴 타임아웃</li>
 *   <li>{@link com.minihttp.example.socket.Step08GracefulShutdownExample} — 새 연결 차단 → 진행 중 연결 마무리</li>
 *   <li>{@link com.minihttp.example.socket.Step09NioSelectorExample} — 논블로킹 채널 + Selector 단일 스레드</li>
 * </ol>
 */
package com.minihttp.example.socket;

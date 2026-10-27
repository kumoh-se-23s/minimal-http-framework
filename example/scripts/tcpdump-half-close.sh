#!/usr/bin/env bash
#
# Step02HalfCloseExample을 실행하면서 루프백(lo)의 TCP 패킷을 tcpdump로 보여 준다.
# half-close 때 FIN과 ACK가 어떤 순서로 오가는지 직접 확인하는 용도.
#
# 사용법:
#   example/scripts/tcpdump-half-close.sh [포트]     (기본 포트 18080)
#
# 필요한 것: tcpdump, 그리고 캡처 권한(root, sudo, 또는 tcpdump에 setcap) — 아래 3) 참고
#
set -euo pipefail

PORT="${1:-18080}"
MAIN_CLASS="com.minihttp.example.socket.Step02HalfCloseExample"

# 스크립트 위치 기준으로 프로젝트 루트로 이동한다. 어디서 실행해도 같게 동작하도록.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# ─── 1) 준비물 확인 ───────────────────────────────────────────

command -v tcpdump >/dev/null || { echo "tcpdump가 없습니다: sudo apt install tcpdump" >&2; exit 1; }

# Gradle을 띄울 JVM. JAVA_HOME이 잘못돼 있으면(예: WSL에서 Windows JDK 경로) PATH의 java로 대신한다.
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    export JAVA_HOME
fi

# 예제를 실행할 JVM. 프로젝트는 Java 26으로 컴파일되므로 Gradle이 받아 둔 26 toolchain을 쓴다.
# 다른 JDK를 쓰려면 RUN_JAVA=/path/to/java 로 지정.
RUN_JAVA="${RUN_JAVA:-$(ls -d "$HOME"/.gradle/jdks/*26*/bin/java 2>/dev/null | head -n 1)}"
[[ -x "$RUN_JAVA" ]] || { echo "Java 26을 찾지 못했습니다. RUN_JAVA=/path/to/java 로 지정하세요." >&2; exit 1; }

# 포트가 이미 쓰이고 있으면 서버가 bind에 실패한다.
if ss -ltn "sport = :$PORT" | grep -q LISTEN; then
    echo "포트 $PORT 가 이미 사용 중입니다. 다른 포트를 인자로 주세요." >&2
    exit 1
fi

# ─── 2) 컴파일 ───────────────────────────────────────────────

echo "▶ 컴파일 중..."
./gradlew :example:compileJava -q

# ─── 3) 캡처 시작 ────────────────────────────────────────────

# 패킷 캡처에는 권한이 필요하다. 가능한 방법을 순서대로 시도한다.
#   1. 이미 root
#   2. tcpdump 바이너리에 캡처 권한(capability)이 붙어 있음 → sudo 없이 실행
#   3. 터미널이 있음 → sudo로 비밀번호를 물어본다
# Claude Code의 `!` 실행처럼 터미널이 없는 환경에서는 3번이 불가능해서 2번이 필요하다.
TCPDUMP_BIN="$(readlink -f "$(command -v tcpdump)")"
if [[ $EUID -eq 0 ]]; then
    SUDO=()
elif getcap "$TCPDUMP_BIN" 2>/dev/null | grep -q cap_net_raw; then
    SUDO=()
elif sudo -n true 2>/dev/null; then
    SUDO=(sudo)
elif [[ -t 0 ]]; then
    SUDO=(sudo)
    # 비밀번호를 먼저 물어 둔다. 캡처 도중에 프롬프트가 끼어들지 않게.
    sudo -v
else
    cat >&2 <<EOF
패킷 캡처 권한이 없고, sudo 비밀번호를 입력할 터미널도 없습니다. 둘 중 하나를 하세요.

  (A) 직접 연 터미널에서 이 스크립트를 실행 (sudo 비밀번호를 물어봅니다)

  (B) 직접 연 터미널에서 한 번만 실행해 tcpdump에 캡처 권한을 붙이기
        sudo setcap cap_net_raw,cap_net_admin=eip $TCPDUMP_BIN
      이후로는 어디서든 sudo 없이 실행됩니다.
      되돌리려면: sudo setcap -r $TCPDUMP_BIN
EOF
    exit 1
fi

# tcpdump 옵션
#   -i lo   루프백 인터페이스 (localhost 통신은 여기로만 지나간다)
#   -n      IP/포트를 이름으로 바꾸지 않는다
#   -l      줄 단위로 바로 출력 (Java 출력과 섞여 보이도록)
#   -ttt    직전 패킷과의 시간 간격 표시
#   -A      페이로드를 ASCII로 출력 (보낸 문자열이 보인다)
echo "▶ tcpdump 시작 (lo, tcp port $PORT)"
echo
"${SUDO[@]}" tcpdump -i lo -n -l -ttt -A "tcp port $PORT" &
TCPDUMP_PID=$!
# 스크립트가 어떻게 끝나든(Ctrl+C 포함) tcpdump를 정리한다.
trap '"${SUDO[@]}" kill "$TCPDUMP_PID" 2>/dev/null || true' EXIT

# tcpdump가 캡처를 시작할 시간을 준다.
sleep 1

# ─── 4) 예제 실행 ────────────────────────────────────────────

echo "──────────────── 예제 출력 + 패킷 ────────────────"
"$RUN_JAVA" -cp example/build/classes/java/main "$MAIN_CLASS" "$PORT"

# 마지막 FIN/ACK가 출력될 시간을 준다.
sleep 1
echo "──────────────────────────────────────────────────"

cat <<'EOF'

패킷 읽는 법
  Flags [S]   SYN        연결 시작
  Flags [S.]  SYN+ACK
  Flags [.]   ACK만       (데이터 없이 확인만 보내는 세그먼트)
  Flags [P.]  PSH+ACK    데이터 전송
  Flags [F.]  FIN+ACK    "이 방향으로는 더 안 보낸다"
  Flags [R]   RST        강제 종료

찾아볼 것
  1. 클라이언트(임시 포트) → 서버 방향 [F.]  : shutdownOutput()
  2. 그 뒤에도 서버 → 클라이언트 [P.]         : 반대 방향은 아직 열려 있다
  3. 그 데이터에 대한 클라이언트의 [.]         : 출력을 닫았어도 ACK는 보낸다
  4. 서버 → 클라이언트 [F.]                    : 서버의 close()
  ※ 데이터와 FIN이 한 세그먼트로 합쳐져 [FP.] 로 보일 수도 있다.
EOF

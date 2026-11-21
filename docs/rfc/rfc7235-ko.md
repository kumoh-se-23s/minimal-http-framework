# 🔐 RFC 7235 — HTTP/1.1 인증 (한국어 정리, 발췌)

> [!WARNING]
> **RFC 7235는 폐기(obsolete)되었습니다.** 현재 표준은 [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110)의 **§11 HTTP Authentication**입니다.
> 같은 폴더의 [RFC 9110 번역](rfc9110-ko.md)에는 §11이 번역되어 있지 않으므로, §11은 [원문 §11](https://www.rfc-editor.org/rfc/rfc9110#section-11)을 보시고, 관련 상태 코드는 번역본의 [15.5.2. 401 Unauthorized](rfc9110-ko.md#1552-401-unauthorized), [15.5.4. 403 Forbidden](rfc9110-ko.md#1554-403-forbidden), [15.5.8. 407 Proxy Authentication Required](rfc9110-ko.md#1558-407-proxy-authentication-required)를 참고하십시오.
> RFC 9110 부록 B.6 "Changes from RFC 7235"에는 **"None."** 이라고 적혀 있습니다. 즉 규범적 내용은 그대로 옮겨졌고, 아래 [RFC 9110에서 달라진 점](#-rfc-9110에서-달라진-점)은 표현·보완 수준의 차이입니다.

> [!NOTE]
> 이 문서는 [RFC 7235](https://www.rfc-editor.org/rfc/rfc7235)의 비공식 한국어 정리입니다. 원문과 차이가 있으면 원문이 우선합니다.
> 구현에 필요한 부분만 골라 옮겼습니다(인사말, 문서 상태, 저작권, IANA 등록 절차, 감사의 글, 참고문헌, 변경 이력은 생략).
> 요구 수준 키워드(MUST, SHOULD, MAY 등)는 번역 뒤 괄호에 원어를 함께 적었습니다. ABNF 문법과 예시는 원문 그대로 두었습니다.
> 구체적인 스킴은 같은 폴더의 [RFC 6750 (Bearer)](rfc6750-ko.md), [RFC 7617 (Basic)](rfc7617-ko.md) 정리를 참고하십시오.

- 원문 저자: R. Fielding(편집, Adobe), J. Reschke(편집, greenbytes)
- 발행: 2014년 6월 · IETF Standards Track · RFC 2616 일부, RFC 2617 일부 대체 · **RFC 9110으로 대체됨**

## 📑 목차

- [🔄 RFC 9110에서 달라진 점](#-rfc-9110에서-달라진-점)
- [🧩 2.1. 챌린지와 응답](#-21-챌린지와-응답-challenge-and-response)
- [🏰 2.2. 보호 공간 (Realm)](#-22-보호-공간-protection-space-realm)
- [🚦 3. 상태 코드 (401 / 407)](#-3-상태-코드-정의)
- [📨 4. 헤더 필드](#-4-헤더-필드-정의)
- [🧪 5.1.2. 새 인증 스킴을 위한 고려 사항](#-512-새-인증-스킴을-위한-고려-사항)
- [🛡️ 6. 보안 고려 사항](#️-6-보안-고려-사항)
- [💡 예시 모음](#-예시-모음)
- [🛠️ minihttp 구현 체크리스트](#️-minihttp-구현-체크리스트)

---

## 🔄 RFC 9110에서 달라진 점

| 항목 | RFC 7235 | RFC 9110 (§11) |
|---|---|---|
| 절 구성 | §2.1 하나에 스킴·파라미터·챌린지·자격 증명이 모두 있음 | §11.1 스킴, §11.2 파라미터, §11.3 챌린지, §11.4 자격 증명으로 나뉨 |
| `WWW-Authenticate` / `Proxy-Authenticate` ABNF | `1#challenge` | `#challenge` (문법상 빈 리스트 허용. 단, 401에는 여전히 챌린지 1개 이상이 필수(MUST)) |
| auth-param 값 표기 | §5.1.2에서 "token과 quoted-string 모두 지원하는 것이 좋다(ought to)" | §11.2에서 **스킴 정의는 송신·수신 양쪽에서 두 표기를 모두 받아들여야 한다**고 명시. 하위 호환을 위해 송신 형식만 한쪽으로 제한하는 것은 가능 |
| 여러 챌린지 예시 | `Newauth ..., Basic realm="simple"` | `Basic realm="simple", Newauth ...` 순서로 바뀜. 그리고 **한 필드 줄에 챌린지 여러 개를 넣는 형식은 일부 UA가 인식하지 못해 상호운용성이 떨어질 수 있다**는 문장이 추가됨 |
| 보호 공간 기준 | canonical root URI (스킴 + authority) | origin (§4.3.1) |
| 새 헤더 | — | `Authentication-Info`, `Proxy-Authentication-Info` (원래 RFC 7615) 를 §11.6.3, §11.7.3에 통합 |
| 쿠키 언급 | — | §11.4 끝에 "여러 커스텀 인증 방식이 Set-Cookie/Cookie로 토큰을 주고받는다"는 참고가 추가됨 |
| 보안 고려 사항 위치 | §6 | §17.16 (§17.16.4 "추가 응답 필드"가 새로 생김) |
| 새 스킴 고려 사항 위치 | §5.1.2 | §16.4.2 |

> [!TIP]
> minihttp 구현에 직접 영향을 주는 차이는 사실상 **"auth-param은 token/quoted-string 둘 다 파싱"** 과 **"여러 챌린지는 가능하면 필드 줄을 나눠서 보내기"** 정도입니다.

---

## 🧩 2.1. 챌린지와 응답 (Challenge and Response)

HTTP는 서버가 클라이언트 요청에 **챌린지(challenge)** 를 던지고, 클라이언트가 **자격 증명(credentials)** 으로 인증 정보를 제공하는 단순한 챌린지-응답 인증 프레임워크를 제공합니다.

- 인증 스킴은 **대소문자를 구분하지 않는 토큰(case-insensitive token)** 으로 식별합니다.
- 스킴 뒤에는 그 스킴으로 인증하는 데 필요한 추가 정보가 옵니다. 이 정보는 다음 둘 중 하나입니다.
  1. 쉼표로 구분한 **파라미터 목록** (`auth-param`)
  2. base64 인코딩 정보를 담을 수 있는 **단일 문자열** (`token68`)
- 인증 파라미터는 `name=value` 쌍이며, **이름 토큰은 대소문자 구분 없이** 비교하고, 각 파라미터 이름은 한 챌린지 안에서 **한 번만 나와야 합니다(MUST)**.

```abnf
auth-scheme    = token

auth-param     = token BWS "=" BWS ( token / quoted-string )

token68        = 1*( ALPHA / DIGIT /
                     "-" / "." / "_" / "~" / "+" / "/" ) *"="

challenge      = auth-scheme [ 1*SP ( token68 / #auth-param ) ]

credentials    = auth-scheme [ 1*SP ( token68 / #auth-param ) ]
```

> [!NOTE]
> `token68`은 URI의 비예약 문자 66개(RFC 3986)에 `+`, `/`, 그리고 끝의 `=` 패딩을 더한 것입니다. 그래서 base64, base64url, base32, base16(hex)을 패딩 유무와 관계없이 담을 수 있지만, **공백은 담을 수 없습니다**.
> `BWS`(bad whitespace)는 `=` 앞뒤에 공백이 "올 수도 있다"는 뜻입니다. 송신자는 만들지 않아야 하지만 수신자는 허용해야 합니다(RFC 9110 §5.6.3).

| 구성 요소 | 의미 | 예 |
|---|---|---|
| `auth-scheme` | 인증 스킴 이름 (대소문자 무시) | `Basic`, `bearer`, `DIGEST` |
| `token68` | 스킴 뒤에 오는 단일 인코딩 값 | `QWxhZGRpbjpvcGVuIHNlc2FtZQ==` |
| `auth-param` | `이름=값` 파라미터 (이름은 대소문자 무시) | `realm="api"`, `error="invalid_token"` |
| `challenge` | 서버가 보내는 "이 방식으로 인증하세요" | `Bearer realm="api"` |
| `credentials` | 클라이언트가 보내는 인증 정보 | `Bearer mF_9.B5f-4.1JqM` |

> [!IMPORTANT]
> 스킴과 그 뒤의 데이터는 **`1*SP`(공백 1개 이상)** 로 구분됩니다. 그러므로 파싱은 "첫 공백 기준으로 스킴과 나머지를 나눈다"가 기본이고, 공백이 여러 개일 수 있다는 점을 고려해야 합니다.

### 상태 코드와 헤더의 짝

- 오리진 서버는 사용자 에이전트의 인가에 챌린지를 걸 때 **401 (Unauthorized)** 응답을 쓰며, 요청한 리소스에 적용할 수 있는 챌린지를 적어도 하나 담은 `WWW-Authenticate` 헤더를 포함합니다.
- 프록시는 클라이언트에 챌린지를 걸 때 **407 (Proxy Authentication Required)** 응답을 쓰며, `Proxy-Authenticate` 헤더를 포함합니다.
- 오리진 서버에 인증하려는 사용자 에이전트는 (보통 401을 받은 뒤지만 꼭 그럴 필요는 없이) 요청에 `Authorization` 헤더를 넣습니다. 프록시에 대해서는 `Proxy-Authorization`을 넣습니다.

> [!NOTE]
> 많은 클라이언트가 **모르는 스킴이 들어 있는 챌린지를 파싱하지 못합니다.** 우회책은 잘 지원되는 스킴(예: `basic`)을 **먼저** 나열하는 것입니다.

```mermaid
sequenceDiagram
    participant C as 클라이언트(UA)
    participant S as 오리진 서버
    C->>S: GET /projects (Authorization 없음)
    S-->>C: 401 Unauthorized<br/>WWW-Authenticate: Bearer realm="api"
    Note over C: 이해하는 스킴 중 가장 안전한 것을 고르고<br/>자격 증명을 준비
    C->>S: GET /projects<br/>Authorization: Bearer mF_9.B5f-4.1JqM
    alt 자격 증명 유효 + 권한 있음
        S-->>C: 200 OK
    else 자격 증명 무효/누락/불완전
        S-->>C: 401 Unauthorized + WWW-Authenticate (새 챌린지일 수 있음)
    else 자격 증명은 유효하지만 권한 부족
        S-->>C: 403 Forbidden
    end
```

### 자격 증명 처리 규칙

- `Authorization`과 `Proxy-Authorization` 값은 (과거에 받았을 수도 있는) 챌린지를 바탕으로, 요청한 리소스의 realm에 대한 클라이언트 자격 증명을 담습니다.
- 사용자 에이전트는 **자신이 이해하는 스킴 중 가장 안전하다고 판단하는 것**을 골라 자격 증명을 만드는 것이 좋습니다(ought to).
- 헤더 값으로 자격 증명을 보내는 것은 하부 연결의 기밀성에 관해 중대한 보안 고려가 따릅니다([§6.1](#61-자격-증명의-기밀성)).

보호된 리소스에 대한 요청이 다음 중 하나라면, 오리진 서버는 요청한 리소스에 적용할 수 있는 (새 것일 수도 있는) 챌린지를 하나 이상 담은 `WWW-Authenticate`와 함께 **401을 보내야 합니다(SHOULD)**.

| 상황 | 예 |
|---|---|
| 자격 증명 **누락** | `Authorization` 헤더 없음 |
| **잘못된** 자격 증명 | 비밀번호 틀림, 서명 검증 실패 |
| **불완전한** 자격 증명 | 왕복이 여러 번 필요한 스킴의 중간 단계 |

프록시도 마찬가지로 프록시 자격 증명이 누락·무효·불완전하면 `Proxy-Authenticate`를 담은 **407을 생성해야 합니다(SHOULD)**.

> [!IMPORTANT]
> **유효한 자격 증명을 받았지만 접근하기에 충분하지 않다면** 서버는 **403 (Forbidden)** 으로 응답하는 것이 좋습니다(ought to). "누구인지 모름" = 401, "누구인지는 알지만 안 됨" = 403 입니다.

HTTP는 접근 인증을 이 챌린지-응답 프레임워크로 제한하지 않습니다. 전송 계층 인증, 메시지 캡슐화, 별도 헤더 필드 같은 다른 방식도 쓸 수 있지만 이 명세는 그런 방식을 정의하지 않습니다.

---

## 🏰 2.2. 보호 공간 (Protection Space, Realm)

- `realm` 인증 파라미터는 **보호 범위를 나타내려는 스킴을 위해 예약**되어 있습니다.
- **보호 공간(protection space)** 은 접근하는 서버의 canonical root URI(유효 요청 URI의 스킴 + authority)와, 있다면 realm 값을 조합해 정의합니다.
- realm을 쓰면 한 서버의 보호 리소스를 여러 보호 공간으로 나눌 수 있고, 각 공간은 자기만의 인증 스킴이나 인가 데이터베이스를 가질 수 있습니다.
- realm 값은 보통 오리진 서버가 정하는 문자열이며, 스킴에 따라 추가 의미가 있을 수 있습니다.
- 한 응답 안에 **같은 스킴이지만 realm이 다른 챌린지가 여러 개** 있을 수 있습니다.

보호 공간은 자격 증명을 **자동으로 적용할 수 있는 범위**를 정합니다. 이전 요청이 인가되었다면, 사용자 에이전트는 스킴·파라미터·사용자 설정(예: 비활성 타임아웃)이 정하는 기간 동안 같은 보호 공간의 다른 요청에 **같은 자격 증명을 재사용할 수 있습니다(MAY)**. 스킴이 특별히 허용하지 않는 한, 하나의 보호 공간은 그 서버의 범위를 넘을 수 없습니다.

> [!CAUTION]
> 역사적 이유로, realm 값은 송신자가 **반드시 quoted-string 문법으로만 생성해야 합니다(MUST)**. 수신자는 오래된 클라이언트와의 호환을 위해 token과 quoted-string을 모두 지원해야 할 수도 있습니다.
>
> ```http
> WWW-Authenticate: Bearer realm="api"      ✅
> WWW-Authenticate: Bearer realm=api        ❌ (송신 금지, 수신은 관대하게)
> ```

---

## 🚦 3. 상태 코드 정의

### 3.1. 401 Unauthorized

401은 대상 리소스에 대한 **유효한 인증 자격 증명이 없어서** 요청이 적용되지 않았다는 뜻입니다.

> [!IMPORTANT]
> 401 응답을 생성하는 서버는 대상 리소스에 적용할 수 있는 챌린지를 **적어도 하나 담은 `WWW-Authenticate` 헤더를 반드시 보내야 합니다(MUST).**

- 요청에 자격 증명이 있었다면, 401은 **그 자격 증명에 대한 인가가 거부되었다**는 뜻입니다.
- 사용자 에이전트는 새로운 또는 교체한 `Authorization` 헤더로 요청을 반복할 수 있습니다(MAY).
- 401 응답이 이전 응답과 **같은 챌린지**를 담고 있고 사용자 에이전트가 이미 한 번 이상 인증을 시도했다면, 응답 본문에는 보통 진단 정보가 있으므로 사용자 에이전트는 그 본문을 사용자에게 보여 줘야 합니다(SHOULD).

> [!TIP]
> 그래서 401 본문에 `{"detail": "Invalid token"}` 같은 진단 메시지를 넣어 두는 것이 의미가 있습니다.

### 3.2. 407 Proxy Authentication Required

401과 비슷하지만, 클라이언트가 **프록시를 쓰기 위해** 스스로를 인증해야 한다는 뜻입니다.

- 프록시는 그 프록시에 적용할 수 있는 챌린지를 담은 `Proxy-Authenticate` 헤더를 **반드시 보내야 합니다(MUST)**.
- 클라이언트는 새로운 또는 교체한 `Proxy-Authorization` 헤더로 요청을 반복할 수 있습니다(MAY).

| 코드 | 보내는 쪽 | 챌린지 헤더 (응답) | 자격 증명 헤더 (요청) |
|---|---|---|---|
| **401** Unauthorized | 오리진 서버 | `WWW-Authenticate` (MUST) | `Authorization` |
| **407** Proxy Authentication Required | 프록시 | `Proxy-Authenticate` (MUST) | `Proxy-Authorization` |
| **403** Forbidden | 오리진 서버 | (불필요) | 자격 증명은 유효하나 권한 부족 |

---

## 📨 4. 헤더 필드 정의

### 4.1. WWW-Authenticate

대상 리소스에 적용할 수 있는 인증 스킴과 파라미터를 알려 주는 **응답** 헤더입니다.

```abnf
WWW-Authenticate = 1#challenge
```

- 401 응답을 생성하는 서버는 챌린지를 하나 이상 담은 `WWW-Authenticate`를 **반드시 보내야 합니다(MUST)**.
- 서버는 다른 응답에도 `WWW-Authenticate`를 넣어 "자격 증명(또는 다른 자격 증명)을 제공하면 응답이 달라질 수 있다"고 알릴 수 있습니다(MAY).
- 응답을 전달하는 프록시는 `WWW-Authenticate` 필드를 **수정해서는 안 됩니다(MUST NOT)**.

> [!WARNING]
> **파싱 주의.** 필드 값 하나에 **챌린지가 여러 개** 있을 수 있고, 각 챌린지에는 **쉼표로 구분된 파라미터 목록**이 있을 수 있으며, 필드 자체도 **여러 번** 나올 수 있습니다. 즉 쉼표가 "챌린지 구분자"인지 "파라미터 구분자"인지 단순 `split(",")`으로는 알 수 없습니다.

```http
WWW-Authenticate: Newauth realm="apps", type=1,
                  title="Login to \"apps\"", Basic realm="simple"
```

이 필드에는 챌린지가 **두 개** 있습니다.

| # | 스킴 | 파라미터 |
|---|---|---|
| 1 | `Newauth` | `realm="apps"`, `type=1`, `title="Login to \"apps\""` |
| 2 | `Basic` | `realm="simple"` |

> [!TIP]
> 구분 요령: 쉼표 뒤에 오는 것이 `token =` 꼴이면 직전 챌린지의 파라미터이고, `token` 뒤에 공백(그리고 token68 또는 파라미터)이 오거나 `=` 없이 끝나면 새 챌린지의 시작입니다. quoted-string 안의 쉼표·이스케이프(`\"`)도 건너뛰어야 합니다.

> [!NOTE]
> `challenge` 문법도 리스트 문법을 쓰므로 "쉼표, 공백, 쉼표" 순서는 앞 챌린지에 붙은 것으로 볼 수도, 챌린지 목록의 빈 항목으로 볼 수도 있습니다. 실제로는 의미에 영향이 없으므로 무해합니다.

### 4.2. Authorization

사용자 에이전트가 오리진 서버에 자신을 인증하게 해 주는 **요청** 헤더입니다(보통 401을 받은 뒤지만 꼭 그럴 필요는 없습니다). 값은 요청 리소스의 realm에 대한 사용자 에이전트의 인증 정보를 담은 `credentials`입니다.

```abnf
Authorization = credentials
```

- 요청이 인증되었고 realm이 지정되었다면, 같은 자격 증명은 그 realm 안의 다른 모든 요청에도 유효하다고 간주됩니다. (챌린지 값에 따라 달라지는 자격 증명이나 동기화된 시계를 쓰는 등, 스킴이 달리 요구하는 경우는 예외입니다.)
- 요청을 전달하는 프록시는 `Authorization` 필드를 **수정해서는 안 됩니다(MUST NOT)**.
- HTTP 캐시의 `Authorization` 처리는 RFC 7234 §3.2(현재 RFC 9111 §3.5)를 따릅니다.

> [!NOTE]
> `WWW-Authenticate`와 달리 `Authorization`은 `credentials` **하나**입니다(리스트가 아님). 따라서 `Authorization` 값에서 여러 스킴을 기대할 필요가 없습니다.

### 4.3. Proxy-Authenticate (요약)

```abnf
Proxy-Authenticate = 1#challenge
```

- 프록시에 적용되는 챌린지를 담습니다. 프록시는 생성하는 모든 407 응답에 `Proxy-Authenticate`를 **하나 이상 보내야 합니다(MUST)**.
- `WWW-Authenticate`와 달리 응답 체인상의 **바로 다음 아웃바운드 클라이언트에만** 적용됩니다(그 프록시를 고른 클라이언트만 자격 증명을 가지고 있을 가능성이 크기 때문).
- 파싱 주의 사항은 §4.1과 같습니다.

### 4.4. Proxy-Authorization (요약)

```abnf
Proxy-Authorization = credentials
```

- 인증을 요구하는 프록시에게 클라이언트(또는 사용자)를 식별시킵니다.
- `Authorization`과 달리 `Proxy-Authenticate`로 인증을 요구한 **바로 다음 인바운드 프록시에만** 적용되며, 체인에서 자격 증명을 기다리던 첫 프록시가 소비합니다.
- 프록시들이 협력해 인증하는 방식이라면 다음 프록시로 자격 증명을 전달할 수 있습니다(MAY).

> [!TIP]
> minihttp는 오리진 서버이므로 `Proxy-*` 헤더를 직접 만들 일은 거의 없습니다. 다만 앞단 리버스 프록시가 있을 때 `Proxy-Authorization`을 애플리케이션 인증 수단으로 착각하지 않도록 주의하십시오.

---

## 🧪 5.1.2. 새 인증 스킴을 위한 고려 사항

> [!NOTE]
> IANA 등록 절차(§5.1, §5.1.1)는 생략했습니다. 등록된 스킴 목록은 <https://www.iana.org/assignments/http-authschemes> 에 있습니다.

HTTP 인증 프레임워크에는 새 스킴의 동작 방식을 제약하는 측면이 있습니다. 구현에 영향이 있는 것만 정리했습니다.

| # | 규칙 | 구현 관점 |
|---|---|---|
| 1 | HTTP 인증은 **무상태(stateless)** 로 가정합니다. 요청을 인증하는 데 필요한 정보는 **모두 그 요청 안에 있어야 합니다(MUST)**. 서버가 이전 요청을 기억하는 것에 의존하면 안 됩니다. 연결에 묶인 인증은 범위 밖이며, 그 연결을 인증된 사용자 외에 누구도 쓸 수 없게 보장하지 않으면 본질적으로 결함이 있습니다. | `resolve(HttpRequest)`는 매 요청의 헤더만 보고 판단해야 합니다. keep-alive 연결 단위로 "이미 인증됨"을 캐시하지 마십시오. |
| 2 | `realm` 파라미터는 §2.2의 보호 공간 정의용으로 예약되어 있으며, 새 스킴은 그 정의와 **양립할 수 없는 방식으로 써서는 안 됩니다(MUST NOT)**. | realm을 다른 의미로 재활용하지 마십시오. |
| 3 | `token68`은 기존 스킴과의 호환용이며 **챌린지·자격 증명당 한 번만** 쓸 수 있습니다. 새 스킴은 확장성을 위해 auth-param 문법을 쓰는 것이 좋습니다(ought to). | `Basic`/`Bearer`의 자격 증명은 token68, 챌린지는 auth-param입니다. 한 값에 둘이 섞이지 않습니다. |
| 4 | 챌린지·자격 증명의 **파싱은 이 명세가 정의하며 새 스킴이 바꿀 수 없습니다.** auth-param을 쓴다면 모든 파라미터는 token과 quoted-string을 **둘 다** 지원하는 것이 좋고(ought to), 문법 제약은 파싱(따옴표 해제) **후의** 값에 정의해야 합니다. 그래야 모든 스킴에 공통 파서를 쓸 수 있습니다. | 스킴별 파서가 아니라 **공통 파서 하나**를 두고, 스킴 클래스는 파싱된 결과만 검증하게 하십시오. |
| 5 | (참고) `realm` 값을 quoted-string으로만 제한한 것은 잘못된 설계였으며 새 파라미터에서 반복하지 말아야 합니다. | — |
| 6 | 새 스킴은 **모르는 확장 파라미터의 처리**를 정의하는 것이 좋습니다. 일반적으로 "반드시 무시(must-ignore)"가 "반드시 이해(must-understand)"보다 낫습니다. | 모르는 auth-param은 오류로 만들지 말고 무시하십시오. |
| 7 | 스킴은 오리진 서버 인증(`WWW-Authenticate`)용인지, 프록시 인증(`Proxy-Authenticate`)용인지, 둘 다인지 문서화해야 합니다. | — |
| 8 | `Authorization` 헤더의 자격 증명은 사용자 에이전트별이므로, 그 요청 범위 안에서 응답 `Cache-Control: private`과 같은 효과를 캐시에 줍니다. **`Authorization`이 아닌 새 헤더로 자격 증명을 나르는 스킴은 캐시를 명시적으로 금지**해야 합니다(`no-store` 요청 지시자나 `private` 응답 지시자 등). | 토큰을 `X-API-Key` 같은 커스텀 헤더로 받는다면 응답에 `Cache-Control: private`(또는 `no-store`)을 붙이십시오. |

---

## 🛡️ 6. 보안 고려 사항

HTTP 인증에 관한 모든 것이 보안 고려 사항이므로 아래 목록이 전부는 아닙니다. 여기서는 프레임워크 일반에 관한 것만 다루며, 개별 스킴의 고려 사항은 각 스킴 명세에 있습니다.

### 6.1. 자격 증명의 기밀성

- 프레임워크는 자격 증명 기밀성을 위한 단일 메커니즘을 정의하지 않습니다. 인코딩 방식은 스킴마다 다르며, 자체 기밀성이 없거나 재전송(replay) 공격 방어가 부족한 스킴에는 불충분합니다.
- 서버가 사용자별 자격 증명을 기대한다면, 내용이 기밀로 유지되더라도 그 교환 자체가 사용자를 식별하는 효과가 있습니다.
- HTTP는 헤더 필드의 기밀 전송을 **하부 전송/세션 계층의 보안 속성에 의존**합니다. 이 프레임워크로 접근을 제한하는 서버는 스킴의 성격에 맞게 연결이 보호되는지 확인해야 하며, 개별 사용자 인증에 의존하는 서비스는 자격 증명 교환 전에 **TLS**를 요구하는 경우가 많습니다.

> [!CAUTION]
> `Basic`은 base64일 뿐 암호화가 아니고, `Bearer` 토큰은 가진 사람이 곧 주인입니다. 둘 다 평문 HTTP 위에서는 그대로 노출됩니다.

### 6.2. 자격 증명과 유휴 클라이언트

- 기존 HTTP 클라이언트는 보통 인증 정보를 **무기한 보관**합니다. HTTP에는 오리진 서버가 클라이언트에게 캐시된 자격 증명을 버리라고 지시하는 메커니즘이 없습니다. 만료·폐기 메커니즘은 스킴 정의의 일부로 정할 수 있습니다.
- 자격 증명 캐싱이 보안 모델을 방해할 수 있는 상황(일부):
  - 클라이언트가 오래 유휴 상태였고, 서버가 사용자에게 다시 자격 증명을 묻게 하고 싶을 때
  - "로그아웃" 같은 세션 종료 표시가 있어, 서버 쪽은 더 이상 자격 증명을 보관할 이유가 없음을 "알" 때
- 자격 증명을 캐시하는 사용자 에이전트는 사용자가 쉽게 그것을 폐기할 수 있는 수단을 제공하는 것이 권장됩니다.

> [!TIP]
> Bearer 토큰이라면 만료 시간(`exp`)과 서버 측 폐기 목록으로 이 문제를 스킴 수준에서 해결합니다.

### 6.3. 보호 공간

- `realm`에만 의존해 보호 공간을 정하는 스킴은 오리진 서버의 **모든 리소스에 자격 증명을 노출**합니다. 한 리소스에 인증 성공한 클라이언트는 같은 서버의 다른 리소스에도 같은 자격 증명을 쓸 수 있으므로, **다른 리소스가 자격 증명을 수집(harvest)** 할 수 있습니다.
- 한 오리진 서버가 같은 canonical root URI 아래에서 여러 당사자의 리소스를 호스팅할 때 특히 문제입니다.
- 완화 방안: 자격 증명에 대한 직접 접근 제한(`Authorization` 헤더 내용을 핸들러에 노출하지 않기), 당사자마다 다른 호스트 이름(또는 포트)으로 보호 공간 분리.

> [!IMPORTANT]
> 프레임워크 차원에서 원본 `Authorization` 값을 로그에 남기거나 아무 핸들러나 읽게 두지 말고, `SecureDependency`가 검증한 결과(사용자 객체 등)만 넘기는 것이 이 권고에 부합합니다.

---

## 💡 예시 모음

**① 자격 증명 없이 보호 리소스 요청 → 401**

```http
GET /projects HTTP/1.1
Host: api.example.com
```

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer realm="api"
Content-Type: application/json

{"detail": "Not authenticated"}
```

**② 여러 스킴 제시 (잘 지원되는 Basic을 먼저, 필드 줄을 나눠서)**

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Basic realm="admin", charset="UTF-8"
WWW-Authenticate: Bearer realm="admin"
```

**③ 자격 증명 전송 (스킴 이름은 대소문자 무시)**

```http
GET /projects HTTP/1.1
Host: api.example.com
Authorization: Bearer mF_9.B5f-4.1JqM
```

```http
Authorization: bearer mF_9.B5f-4.1JqM
Authorization: BASIC QWxhZGRpbjpvcGVuIHNlc2FtZQ==
```

**④ 인증은 성공했지만 권한 부족 → 403**

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{"detail": "Not enough permissions"}
```

**⑤ 원문 §4.1의 복합 챌린지**

```http
WWW-Authenticate: Newauth realm="apps", type=1,
                  title="Login to \"apps\"", Basic realm="simple"
```

---

## 🛠️ minihttp 구현 체크리스트

`SecureDependency<T>` / `BearerSchema<T>` / `BasicSchema<T>`의 `resolve(HttpRequest)`를 구현할 때 지킬 점입니다.

> [!WARNING]
> 현재 `BearerSchema.resolve()`는 `Authorization` 헤더가 **없을 때** `HttpStatusException(UNAUTHORIZED, "Not authenticated")`를 `WWW-Authenticate` 없이 던집니다. 스킴 불일치 경로처럼 `Map.of(HttpHeaderField.WWW_AUTHENTICATE, ...)`를 함께 넘겨야 §3.1의 MUST를 지킵니다. 서브클래스의 `authenticate()`가 토큰 검증 실패로 401을 던질 때도 마찬가지이므로, 401 생성을 공통 헬퍼로 모으는 편이 안전합니다.

### ✅ 응답 쪽 (401 / 403)

- [ ] **모든 401에 `WWW-Authenticate`를 넣습니다(MUST, §3.1·§4.1).** "헤더 없음", "스킴 불일치", "토큰 검증 실패" 등 **모든 401 경로**에서 빠지면 안 됩니다.
- [ ] 챌린지 값은 최소 스킴 이름(`Bearer`, `Basic`)이고, realm을 붙인다면 **반드시 quoted-string**으로 생성합니다(`realm="api"`, §2.2 MUST).
- [ ] Basic 챌린지에는 `charset="UTF-8"`을 붙일 수 있습니다(RFC 7617 참고). Bearer의 `error`, `error_description`, `scope` 파라미터는 RFC 6750 정리를 참고하십시오.
- [ ] **인증은 성공했지만 권한이 부족하면 403**을 던집니다(§2.1). 403에는 `WWW-Authenticate`가 필요 없습니다. 권한 검사는 `SecureDependency`가 아닌 별도 의존성/핸들러에서 하는 구조라면 거기서 403을 던지게 합니다.
- [ ] 401 본문에 진단 메시지(`detail`)를 넣습니다. 같은 챌린지가 반복될 때 UA가 이 본문을 보여 주기 때문입니다(§3.1 SHOULD).
- [ ] 여러 스킴을 동시에 받는 의존성을 만든다면 챌린지를 **필드 줄을 나눠** 보내고, 잘 지원되는 스킴을 먼저 둡니다(§2.1 Note, RFC 9110 §11.6.1). 이를 위해 응답 헤더 맵이 같은 이름의 헤더를 여러 개 담을 수 있어야 합니다.

### ✅ 요청 파싱 쪽 (`Authorization`)

- [ ] **스킴 이름은 대소문자 무시**로 비교합니다(`equalsIgnoreCase`). `bearer`, `BEARER` 모두 허용합니다(§2.1).
- [ ] **첫 공백 기준으로** 스킴과 나머지를 나눕니다. `credentials = auth-scheme [ 1*SP ( token68 / #auth-param ) ]` 이므로 공백이 여러 개일 수 있습니다. `split(" ")[0]`은 스킴 추출에는 동작하지만, 나머지를 얻을 때 `split(" ")[1]`을 쓰면 공백 여러 개에서 빈 문자열이 나옵니다. `indexOf(' ')` 후 나머지를 `strip()` 하는 방식을 권합니다.
- [ ] `authenticate()`에는 **스킴을 뗀 자격 증명 부분만** 넘기는 것을 고려하십시오. 현재는 `authData` 전체(`"Bearer xxx"`)가 넘어가므로, 서브클래스마다 다시 잘라야 합니다.
- [ ] 스킴만 있고 값이 없는 경우(`Authorization: Bearer`), 값이 비어 있거나 공백뿐인 경우는 **401 + `WWW-Authenticate`** 로 처리합니다.
- [ ] Bearer/Basic의 값은 `token68`이므로 **공백이 들어 있으면 형식 오류**입니다. 허용 문자는 `A-Z a-z 0-9 - . _ ~ + /` 와 끝의 `=` 패딩뿐입니다.
- [ ] `Authorization`은 리스트가 아닌 단일 `credentials`입니다(§4.2). 같은 요청에 `Authorization`이 여러 번 오면 모호하므로 400 또는 401로 거절하는 쪽이 안전합니다(원문이 명시한 규칙은 아니며 방어적 선택입니다).
- [ ] 공통 파서를 둔다면 auth-param은 `token`과 `quoted-string`을 **둘 다** 받고(§5.1.2, RFC 9110 §11.2), 파라미터 이름은 대소문자 무시, 같은 이름 중복은 오류(MUST once), 모르는 파라미터는 무시(must-ignore)합니다.

### ✅ 설계·보안

- [ ] 인증은 **무상태**입니다(§5.1.2 MUST). 매 요청의 헤더만으로 판단하고, 연결 단위로 인증 결과를 캐시하지 않습니다.
- [ ] 원본 `Authorization` 값을 로그나 감사 기록에 그대로 남기지 않고, 핸들러에는 검증 결과(`T`)만 전달합니다(§6.3).
- [ ] 운영 환경에서는 TLS 위에서만 자격 증명을 받도록 안내합니다(§6.1).
- [ ] `Authorization`이 아닌 커스텀 헤더(예: API 키)로 인증하는 의존성을 추가한다면 응답에 `Cache-Control: private` 또는 `no-store`를 붙입니다(§5.1.2).
- [ ] `Proxy-Authorization`은 애플리케이션 인증에 쓰지 않습니다(§4.4).

### ✅ OpenAPI `securitySchemes` 출력

| 의존성 | OpenAPI 출력 | 대응하는 RFC 요소 |
|---|---|---|
| `BearerSchema<T>` | `{"type": "http", "scheme": "bearer"}` (선택: `"bearerFormat": "JWT"`) | `auth-scheme` = `Bearer` (RFC 6750) |
| `BasicSchema<T>` | `{"type": "http", "scheme": "basic"}` | `auth-scheme` = `Basic` (RFC 7617) |

> [!NOTE]
> OpenAPI 3의 `type: http`의 `scheme` 값은 RFC 7235(현 RFC 9110 §11)가 정의한 `Authorization` 스킴 이름이어야 하며, IANA 인증 스킴 레지스트리에 등록된 값을 쓰도록 되어 있습니다. 스킴 이름이 대소문자를 구분하지 않으므로 OpenAPI 쪽에서는 관례상 소문자(`bearer`, `basic`)를 씁니다.

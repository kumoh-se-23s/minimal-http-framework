# 🔐 RFC 7617 — 'Basic' HTTP 인증 스킴 (한국어 정리·번역)

> 이 문서는 [RFC 7617](https://www.rfc-editor.org/rfc/rfc7617)의 비공식 한국어 정리·번역입니다. 원문과 차이가 있으면 원문이 우선합니다.
> 구현에 필요한 내용(2장, 3장, 4장과 부록 일부)만 옮겼습니다. 인삿말, 상태 고지, 저작권, IANA, 참고문헌, 감사의 글은 뺐습니다.
> 요구 수준 키워드(MUST, SHOULD, MAY 등)는 번역문 뒤에 괄호로 원어를 함께 적었습니다. 예시는 원문 그대로 두었습니다.

- 원문 저자: J. Reschke(greenbytes)
- 발행: 2015년 9월 · IETF Standards Track · **RFC 2617 대체(Obsoletes)**

> [!IMPORTANT]
> **이 RFC의 위치**
> - RFC 7617은 **RFC 7235(HTTP/1.1 인증 프레임워크)** 위에서 `Basic`이라는 *스킴 하나*를 정의하는 문서입니다. challenge/credentials의 일반 문법, `401`, `WWW-Authenticate`, `Authorization`, realm·보호 공간 개념은 모두 RFC 7235의 것입니다.
> - RFC 7235는 현재 **[RFC 9110 §11](rfc9110-ko.md)** 로 흡수되어 대체되었습니다. 이 문서에서 "RFC 7235 §X"라고 적은 부분은 RFC 9110 §11의 해당 내용으로 읽으면 됩니다. → 프레임워크 번역은 [rfc7235-ko.md](rfc7235-ko.md)를 참고하세요.
> - 예전에는 Basic이 **RFC 2617 §2**에 정의되어 있었습니다. RFC 7617은 그 Basic 부분을 대체하며, RFC 7235·7615·7616과 함께 RFC 2617 전체를 폐기합니다.
> - 같은 프레임워크 위의 다른 스킴인 `Bearer`는 [rfc6750-ko.md](rfc6750-ko.md)에 있습니다.

## 📑 목차

- [1. 개요](#-1-개요-1)
- [2. 'Basic' 인증 스킴](#-2-basic-인증-스킴-2)
- [2.1. 'charset' 인증 파라미터](#-21-charset-인증-파라미터-21)
- [2.2. 자격 증명 재사용](#-22-자격-증명-재사용-22)
- [3. 국제화 고려사항](#-3-국제화-고려사항-3)
- [4. 보안 고려사항](#️-4-보안-고려사항-4)
- [부록 A·B 요점](#-부록-ab-요점)
- [🛠️ minihttp 구현 체크리스트](#️-minihttp-구현-체크리스트)

---

## 📖 1. 개요 (§1)

Basic 스킴은 자격 증명(credentials)을 **user-id/password 쌍을 Base64로 인코딩**해서 보내는 HTTP 인증 스킴입니다.

> [!WARNING]
> Base64는 **암호화가 아니라 단순 인코딩**입니다. user-id와 password가 네트워크에 사실상 평문(cleartext)으로 흐르기 때문에, TLS 같은 외부 보안 수단과 함께 쓰지 않는 한 안전한 인증 방법으로 보지 않습니다.

RFC 2617과 비교해 새로 생긴 것은 국제화 문제를 풀기 위한 `charset` 인증 파라미터(§2.1)입니다.

용어:

| 용어 | 뜻 | 정의 위치 |
|---|---|---|
| 보호 공간(protection space) | 같은 자격 증명을 공유하는 리소스 묶음 | RFC 7235 §2.2 (현 RFC 9110 §11.5) |
| realm | 서버가 보호 공간을 구분하려고 붙인 이름 문자열 | RFC 7235 §2.2 |
| user-pass | `user-id ":" password` 로 이어 붙인 문자열 | 이 문서 §2 |
| (character) repertoire / character encoding scheme | 문자 집합 / 문자를 바이트로 바꾸는 방식 | RFC 6365 §2 |

---

## 🧩 2. 'Basic' 인증 스킴 (§2)

Basic 스킴은 클라이언트가 **보호 공간(realm)마다** user-id와 password로 자신을 인증해야 한다는 모델을 따릅니다. realm 값은 자유 형식 문자열이며, **같은 서버의 다른 realm과 같은지 비교하는 용도로만** 쓸 수 있습니다. 서버는 요청한 리소스에 적용되는 보호 공간에 대해 user-id와 password를 검증할 수 있을 때만 요청을 처리합니다.

### 📨 Challenge (서버 → 클라이언트)

| 항목 | 규칙 |
|---|---|
| 스킴 이름 | `Basic` |
| `realm` 파라미터 | **필수(REQUIRED)** (RFC 7235 §2.2) |
| `charset` 파라미터 | 선택(OPTIONAL) — §2.1 |
| 그 밖의 파라미터 | 정의되어 있지 않습니다. 모르는 파라미터는 수신자가 **반드시 무시해야 합니다(MUST be ignored)**. 새 파라미터는 이 명세를 개정해야만 추가할 수 있습니다. |

> [!NOTE]
> **스킴 이름과 파라미터 이름은 모두 대소문자를 구분하지 않고(case-insensitively) 비교합니다.** 즉 `basic`, `BASIC`, `Basic`은 모두 같습니다. challenge를 올바르게 파싱하는 것의 복잡함은 RFC 7235 §4.1에 설명되어 있습니다.

서버는 보호 공간 안의 URI에 대해 자격 증명 **없는** 요청을 받으면, `401 (Unauthorized)`(RFC 7235 §3.1)와 `WWW-Authenticate` 헤더(RFC 7235 §4.1)로 challenge를 돌려줄 수 있습니다.

```http
HTTP/1.1 401 Unauthorized
Date: Mon, 04 Feb 2014 16:50:53 GMT
WWW-Authenticate: Basic realm="WallyWorld"
```

여기서 `"WallyWorld"`는 서버가 보호 공간을 식별하려고 붙인 문자열입니다.

> [!TIP]
> 프록시는 같은 방식으로 `407 (Proxy Authentication Required)`(RFC 7235 §3.2)와 `Proxy-Authenticate` 헤더(RFC 7235 §4.3)를 사용합니다.

### 🔑 Credentials (클라이언트 → 서버)

credentials에는 RFC 7235 §2.1의 **`token68`** 문법을 사용합니다. 값은 user-id와 password로부터 다음 순서로 만듭니다.

1. 사용자에게서 user-id와 password를 얻습니다.
2. user-id, 콜론(`:`) **한 개**, password를 이어 붙여 **user-pass**를 만듭니다.
3. user-pass를 **바이트열(octet sequence)** 로 인코딩합니다. (문자 인코딩은 아래와 §2.1 참고)
4. 그 바이트열을 **Base64**(RFC 4648 §4)로 인코딩해 US-ASCII 문자열을 얻습니다. 이것이 **basic-credentials**입니다.

```mermaid
flowchart LR
    A["user-id<br/>Aladdin"] --> C["user-pass<br/>Aladdin:open sesame"]
    B["password<br/>open sesame"] --> C
    C -- "문자 인코딩<br/>(UTF-8 등)" --> D["바이트열<br/>41 6C 61 ... 6D 65"]
    D -- "Base64<br/>(RFC 4648 §4)" --> E["token68<br/>QWxhZGRpbjpvcGVuIHNlc2FtZQ=="]
    E --> F["Authorization: Basic QWxh..."]
```

```http
Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
```

#### 문법 (ABNF)

> [!NOTE]
> RFC 7617 본문에는 **별도의 ABNF 블록이 없습니다.** 원문은 "credentials에는 RFC 7235의 token68 문법을 쓴다"고만 말하고, user-pass 구성은 위 1~4단계의 산문으로 설명합니다. 아래는 이해를 돕기 위해 RFC 7235 §2.1의 문법과 이 문서의 산문 규칙을 합쳐 정리한 것입니다(`user-pass` 이하 3줄은 비공식 표기).

```abnf
; RFC 7235 §2.1 (현 RFC 9110 §11.3, §11.4)
credentials = auth-scheme [ 1*SP ( token68 / #auth-param ) ]
token68     = 1*( ALPHA / DIGIT / "-" / "." / "_" / "~" / "+" / "/" ) *"="

; Basic 에서는 auth-scheme = "Basic", 값은 token68 하나
; token68 = Base64( encode( user-pass ) )

; ---- 비공식: RFC 7617 §2 산문 규칙을 옮긴 것 ----
user-pass   = user-id ":" password
user-id     = *( 콜론과 CTL을 제외한 문자 )
password    = *( CTL을 제외한 문자 )        ; 콜론 허용
```

#### 문자 인코딩 (기본값은 정의하지 않음)

원래 정의(RFC 2617)는 user-pass를 바이트로 바꿀 문자 인코딩을 정하지 않았습니다. 실제 구현은 ISO-8859-1 같은 로케일 의존 인코딩이나 UTF-8을 썼습니다. 하위 호환을 위해 이 명세도 **기본 인코딩을 여전히 정하지 않습니다.** 단, **US-ASCII와 호환**되어야 합니다(모든 US-ASCII 문자가 같은 코드값의 1바이트로 매핑되어야 함). UTF-8을 원하는 서버는 §2.1의 `charset`으로 알립니다.

#### user-id / password 제약

> [!CAUTION]
> - user-id와 password에는 **제어 문자(CTL, RFC 5234 부록 B.1: `%x00-1F / %x7F`)가 들어가서는 안 됩니다(MUST NOT).**
> - **콜론이 들어간 user-id는 유효하지 않습니다.** user-pass에서 **첫 번째 콜론**이 user-id와 password를 나누고, 첫 콜론 뒤의 텍스트는 전부 password입니다. 따라서 콜론을 포함한 user-id는 user-pass로 인코딩할 수 없습니다.
> - 반대로 **password에는 콜론이 들어갈 수 있습니다.**

> [!WARNING]
> 많은 사용자 에이전트가 사용자가 입력한 user-id에 콜론이 있는지 **검사하지 않고** user-pass를 만듭니다. 그러면 수신자는 사용자 이름 입력의 일부를 password의 일부로 취급하게 됩니다. (예: user-id `a:b`, password `c` → 서버에는 user-id `a`, password `b:c`로 보입니다.)

| user-pass (디코딩 결과) | user-id | password |
|---|---|---|
| `Aladdin:open sesame` | `Aladdin` | `open sesame` |
| `admin:pa:ss` | `admin` | `pa:ss` |
| `admin:` | `admin` | `` (빈 문자열) |
| `:secret` | `` (빈 문자열) | `secret` |
| `admin` (콜론 없음) | — | — → **형식 오류** |

> [!NOTE]
> 마지막 세 줄(빈 값, 콜론 없음)은 원문이 직접 다루지 않는 경우를 구현 관점에서 정리한 것입니다. 원문 산문상 user-pass는 반드시 콜론 하나를 포함하므로, 콜론이 없으면 Basic credentials로 해석할 수 없습니다.

---

## 🌐 2.1. 'charset' 인증 파라미터 (§2.1)

서버는 challenge에 `charset` 파라미터를 넣어, user-pass(바이트열)를 만들 때 사용자 에이전트가 쓰기를 **기대하는** 문자 인코딩을 알릴 수 있습니다. 이 정보는 **순전히 권고(purely advisory)** 입니다.

| 항목 | 규칙 |
|---|---|
| 허용 값 | **`"UTF-8"` 하나뿐**입니다. 대소문자 구분 없이 비교합니다(RFC 2978 §2.3). 다른 값은 향후를 위해 예약되어 있습니다. |
| 의미 | 문자 데이터를 **유니코드 정규화 형식 C(NFC, RFC 5198 §3)** 로 바꾼 뒤 **UTF-8**(RFC 3629)로 인코딩하기를 서버가 기대한다는 뜻입니다. |
| user-id | 수신자는 RFC 7613 §3.3 **"UsernameCasePreserved"** 프로필의 모든 문자를 **반드시 지원해야 합니다(MUST)**. 단 콜론(`:`)은 제외합니다. |
| password | 수신자는 RFC 7613 §4.2 **"OpaqueString"** 프로필의 모든 문자를 **반드시 지원해야 합니다(MUST)**. |
| 위치 | **challenge에만** 정의됩니다. Basic의 credentials는 token68 하나뿐이라 확장할 수 없기 때문입니다. |

> [!NOTE]
> 이름이 `charset`인 것은 RFC 2831 §2.1.1과 맞추기 위해서입니다. 원문 스스로도 이 파라미터는 메시지 자체가 아니라 서버의 기대를 나타내므로 `accept-charset`이 더 나은 이름이었을 것이라고 적고 있습니다.

`"foo"` realm에서 Basic 인증을 요구하면서 UTF-8을 선호한다고 알리는 예:

```http
WWW-Authenticate: Basic realm="foo", charset="UTF-8"
```

파라미터 값은 token이나 quoted-string 둘 다 됩니다. 이 예는 quoted-string을 골랐습니다.

사용자 이름이 `test`, 비밀번호가 `123` 뒤에 U+00A3(POUND SIGN, `£`)이 붙은 문자열이라면, UTF-8로 만든 user-pass 바이트열은 다음과 같습니다.

```text
't' 'e' 's' 't' ':' '1' '2' '3' pound
 74  65  73  74  3A  31  32  33  C2  A3
```

이를 Base64로 인코딩하면 `dGVzdDoxMjPCow==`가 됩니다.

```http
Authorization: Basic dGVzdDoxMjPCow==
```

프록시 인증이라면:

```http
Proxy-Authorization: Basic dGVzdDoxMjPCow==
```

---

## 🗂️ 2.2. 자격 증명 재사용 (§2.2)

인증에 성공한 요청의 절대 URI(RFC 3986 §4.3)에서, **경로(path)의 마지막 슬래시(`/`) 뒤를 모두 잘라낸 것**이 그 요청의 **인증 범위(authentication scope)** 입니다.

- 클라이언트는 인증 범위와 **접두사(prefix)가 일치하는** URI의 리소스도 그 요청의 realm이 가리키는 같은 보호 공간에 속한다고 **가정해야 합니다(SHOULD)**.
- 클라이언트는 그 공간의 리소스를 요청할 때, 서버의 새 challenge를 받지 않고도 `Authorization` 헤더를 **미리(preemptively) 보내도 됩니다(MAY)**.
- 마찬가지로 프록시에 요청할 때도 새 challenge 없이 `Proxy-Authorization`에 user-id/password를 재사용해도 됩니다(MAY).

예: `http://example.com/docs/index.html` 에 인증된 요청을 보냈다면 인증 범위는 `http://example.com/docs/` 입니다.

| URI | 같은 자격 증명 사용? |
|---|---|
| `http://example.com/docs/` | ✅ 범위 안 |
| `http://example.com/docs/test.doc` | ✅ 범위 안 |
| `http://example.com/docs/?page=1` | ✅ 범위 안 |
| `http://example.com/other/` | ❌ 범위 밖 (경로 다름) |
| `https://example.com/docs/` | ❌ 범위 밖 (스킴 다름) |

> [!NOTE]
> 한 URI가 여러 인증 범위(예: `http://example.com/`과 `http://example.com/docs/`)에 동시에 속할 수 있습니다. 이 경우 어느 쪽을 우선할지는 이 명세가 정하지 않습니다.

---

## 🌏 3. 국제화 고려사항 (§3)

- US-ASCII 범위 밖의 문자가 들어간 user-id/password는, 양쪽이 어떤 문자 인코딩을 쓸지 합의하지 않는 한 **상호운용성 문제**를 일으킵니다.
- 서버는 `charset="UTF-8"`(§2.1)로 UTF-8 선호를 알려, 클라이언트가 UTF-8로 바꿀 가능성을 높일 수 있습니다.
- `realm` 값도 텍스트로 볼 수 있지만, RFC 7235는 비 US-ASCII 문자를 안정적으로 전달하는 방법을 정의하지 않습니다. 알려진 문제로, 그 명세의 개정이 필요한 사항입니다.

> [!TIP]
> 실무적으로는 **realm은 ASCII로만** 짓는 것이 안전합니다.

---

## 🛡️ 4. 보안 고려사항 (§4)

| 위험 | 내용 | 대응 |
|---|---|---|
| 🔓 평문 전송 | Basic은 안전한 인증 방법이 아니며, 본문(entity)도 전혀 보호하지 않습니다. **가장 심각한 결함은 비밀번호가 평문으로 네트워크를 지나간다는 점**입니다. | HTTPS(RFC 2818) 같은 보강 없이 민감하거나 가치 있는 정보를 보호하는 데 **써서는 안 됩니다(SHOULD NOT)**. |
| 🔁 비밀번호 재사용 | "식별용(예: 사용 통계)이라 위험 없다"는 생각은 **서버가 user-id와 password를 직접 발급하고 사용자가 고를 수 없을 때만** 맞습니다. 사용자는 한 비밀번호를 여러 곳에 재사용하므로, 사용자가 비밀번호를 정할 수 있으면 다른 시스템(이메일, 건강 포털 등)까지 위험해집니다. 보안과 프라이버시(RFC 6973) 문제가 함께 생깁니다. | 비밀번호 DB를 안전하게 관리합니다. |
| 🎭 가짜 서버 | 사용자가 적대적 서버나 게이트웨이를 진짜로 믿으면, 공격자가 비밀번호를 요청해 저장한 뒤 오류인 척할 수 있습니다. | 서버 구현자는 이런 위장을 막아야 합니다. 기존 연결의 메시지 프레이밍을 가로챌 수 있는 구성 요소(예: RFC 3875 §5의 NPH 스크립트)는 조심해서 쓰거나 아예 쓰지 않습니다. |
| 💾 비밀번호 저장 | 서버와 프록시는 검증을 위해 비밀번호를 어떤 형태로든 저장해야 합니다. 사용자는 약한 비밀번호를 고르고 realm 사이에서 재사용합니다. | 유출돼도 쉽게 복원되지 않게 저장합니다. **평문이나 솔트 없는 해시(unsalted digest)로 저장하지 않아야 합니다.** (참고: Password Hashing Competition, <https://password-hashing.net>) |
| 🔤 UTF-8·정규화 | UTF-8과 정규화 사용은 추가 보안 고려사항을 낳습니다. | RFC 3629 §10, RFC 5198 §6 참고 |

> [!WARNING]
> 원문 §4는 "콜론이 포함된 비밀번호"를 따로 다루지 않습니다. 콜론 관련 규칙은 §2에 있습니다: **첫 번째 콜론 기준 분리, password에는 콜론 허용, user-id에는 콜론 불가.** 이를 잘못 구현하면(예: 마지막 콜론 기준 분리, `split(":")` 결과 개수를 2로 강제) 콜론이 든 정상 비밀번호를 가진 사용자가 로그인하지 못합니다.

---

## 📎 부록 A·B 요점

- **부록 A (RFC 2617 대비 변경)**: 정의를 RFC 7235 방식에 맞게 다시 썼고, `charset` 파라미터를 추가했습니다. `charset`은 권고일 뿐이라 기존 구현은 바꿀 필요가 없습니다.
- **부록 B.2 (서버 배포)**: UTF-8을 지원하려는 서버는 challenge에 `charset`을 넣어 옵트인할 수 있습니다. 모든 클라이언트가 바뀔 때까지 서버는 UTF-8과 레거시 인코딩을 둘 다 받을 수 있으므로, UTF-8 처리가 실패(디코딩 실패 또는 user-id/password 불일치)하면 레거시 인코딩으로 재시도할 수도 있습니다. 다만 일부 시스템은 반복 실패를 비밀번호 추측 공격으로 볼 수 있으니 조심해야 합니다.
- **부록 B.3**: 기본값을 UTF-8로 바로 바꾸지 않은 이유는, ISO-8859-1 등 로컬 인코딩을 기대하는 기존 사이트가 깨지기 때문입니다.

---

## 🛠️ minihttp 구현 체크리스트

`BasicSchema<T>`의 `resolve(HttpRequest)`를 완성할 때 지킬 점입니다. ([BearerSchema](../../src/main/java/com/minihttp/core/route/dependency/security/BearerSchema.java)와 같은 흐름을 따르면 됩니다.)

**요청 파싱**

- [ ] `Authorization` 헤더가 없으면 → `401` + `WWW-Authenticate` challenge.
- [ ] 스킴 이름 비교는 **대소문자 무시** (`"Basic".equalsIgnoreCase(...)`) — §2.
- [ ] 스킴과 token68 사이 공백(`1*SP`)을 기준으로 나누고, token68이 비어 있으면 `401`.
- [ ] token68을 **Base64(RFC 4648 §4, 표준 알파벳 — URL-safe 아님)** 로 디코딩. `IllegalArgumentException` 등 **디코딩 실패 시 `401`** (500이 아님).
- [ ] 디코딩한 바이트를 **UTF-8**로 문자열 변환. 엄격하게 하려면 `CharsetDecoder`를 `CodingErrorAction.REPORT`로 두어 잘못된 바이트열도 `401`로 처리.
- [ ] **첫 번째 콜론** 기준으로 분리 (`indexOf(':')` 또는 `split(":", 2)`). 콜론이 없으면 `401`. **password에는 콜론 허용** — `split(":")`을 limit 없이 쓰면 안 됩니다.
- [ ] user-id/password에 **제어 문자(CTL, `%x00-1F`, `%x7F`)** 가 있으면 거부 — §2 MUST NOT.

**401 응답**

- [ ] `WWW-Authenticate: Basic realm="...", charset="UTF-8"` — **`realm`은 필수(REQUIRED)**, `charset`은 선택이지만 UTF-8로 디코딩한다면 넣는 것이 맞습니다. (Bearer처럼 `"Basic"`만 보내면 RFC 위반입니다.)
- [ ] realm은 ASCII 문자열로, 서브클래스가 지정할 수 있게 (예: 생성자 인자나 `protected String realm()`).
- [ ] realm 값 안의 `"`와 `\`는 quoted-string 규칙에 따라 이스케이프.

**인증 훅 설계**

- [ ] 파싱이 끝난 뒤의 인증 로직은 `authenticate(String username, String password)`처럼 **두 값을 받는 것이 자연스럽습니다.** Basic은 스킴 단계에서 이미 user-id/password로 구조가 정해지므로, 서브클래스가 Base64나 콜론 분리를 다시 할 이유가 없습니다. (현재 `SecureDependency`의 `authenticate(String authData)` 하나로는 이 구조를 표현하기 어렵습니다.)
- [ ] 자격 증명 불일치도 `401` + 같은 challenge. 구분된 메시지("없는 사용자" vs "비밀번호 틀림")로 계정 존재 여부를 흘리지 않기.
- [ ] 비밀번호 비교는 상수 시간 비교(`MessageDigest.isEqual`)와 솔트 해시 저장 — §4.

**운영**

- [ ] TLS(HTTPS) 없이 쓰지 않기 — §4 SHOULD NOT.
- [ ] 모르는 challenge 파라미터는 무시 (클라이언트 측을 구현할 경우) — §2 MUST.

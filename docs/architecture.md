# architecture.md

백엔드 구조와 설계 결정. **이 문서가 코드보다 우선한다.**
기능·정책(무엇을)의 원본은 `docs/plan.md`, 구조·기술 선택(어떻게)의 원본은 이 문서다. 어긋나면 이 문서를 고친다. 단 plan.md 의 `TBD` 는 구현하지 않는다.

---

## 1. 전체 위치

```
[프론트 레포 · 별도 팀 3명]
        │ HTTP (계약: docs/api-spec.md)
        ▼
    nginx (EC2)
        ▼
  Spring Boot API   ← 이 레포
        │
        ├─▶ PostgreSQL 16
        ├─▶ Gemini API (해석 각색)
        ├─▶ SMTP (인증 메일)
        └─▶ Kakao OAuth (로그인 code 교환)
```

프론트는 별도 레포·별도 팀이다. **우리가 보장하는 건 `docs/api-spec.md` 뿐이다.**

---

## 2. 스택

| 영역 | 선택 |
|---|---|
| Language | Java 17 |
| Framework | **Spring Boot 4.1.1** (Spring Framework 7) |
| Build | Gradle Groovy |
| DB | PostgreSQL 16 |
| ORM | Spring Data JPA |
| Migration | Flyway (`ddl-auto: validate`) |
| Mail | Spring Mail (SMTP) |
| Auth | **JWT** (HS256) + 카카오 OAuth 2.0 code 교환(RestClient). Spring Security·세션은 쓰지 않는다. 2026-09-21 확정, §4 |
| LLM | **Gemini Flash** via `com.google.genai:google-genai` (무료 티어) |
| 만세력 | `cn.6tail:lunar` (MIT) + 한국 음력표 `saju/KoreanLunarCalendar` |
| API Docs | **springdoc-openapi 3.1.1** |
| Monitoring | Actuator |
| Test | JUnit5, Mockito, Testcontainers |
| Infra | Docker, EC2, nginx, GitHub Actions |

### 의도적으로 뺀 것

| 항목 | 이유 | 넣을 시점 |
|---|---|---|
| Spring Security | 로그인은 JWT 인터셉터로 충분하다. 인증 경로가 `/api/me`·`/api/dating/**`·`/api/wallet/**` 뿐이고 세션·CSRF·폼 로그인이 없다 | 역할(권한) 체계나 관리자 API 가 필요해질 때 |
| Redis | 토큰 TTL은 `expires_at` 컬럼으로 충분. 컨테이너를 늘리지 않는다 | 조회 1초 초과 또는 풀 고갈 시 |
| ~~JWT~~ | **2026-09-21 V1 도입.** 예전 사유(무효화가 까다롭다)는 로그아웃·기기 관리·토큰 폐기를 하지 않기로 하면서 사라졌다. §4 | 도입 결정 (라이브러리는 팀 승인 대기, §9) |
| QueryDSL | 설정 비용이 1주 일정에 안 맞는다 | 복잡 쿼리 발생 시 |
| H2 | PostgreSQL과 문법이 달라 로컬 통과/배포 실패가 난다 | 없음 |

### Spring Boot 4 주의사항

- Spring Framework 7 / Jakarta EE 11 / Servlet 6.1 베이스라인
- **Jackson 3**. Jackson 2 전용 API를 쓰면 깨진다
- Boot 3에서 deprecated였던 API는 **제거**됐다
- **springdoc은 반드시 3.x.** 2.x는 기동에 실패한다
- AI 도구의 학습 데이터 대부분이 Boot 3 기준이다. 생성 결과를 검증할 것
- JWT 라이브러리가 Jackson 2 에 의존하면 Boot 4(Jackson 3)와 공존하는지 확인한다. 후보는 Nimbus JOSE+JWT, jjwt

---

## 3. 패키지 구조

기술 레이어별(`controller/`, `service/`)이 아니라 **도메인형**이다.
담당자 경계와 코드 구조를 일치시켜야 3명이 서로 안 겹친다.

```
com.darkness.wks
├── WksApplication.java
├── common/                      AGENTS.md ○   3인 합의
│   ├── response/       ApiResponse, ErrorResponse
│   ├── exception/      BusinessException, ErrorCode, GlobalExceptionHandler
│   └── config/         WebConfig, OpenApiConfig, TraceIdFilter, GeminiConfig
│
├── saju/                        AGENTS.md ○   차은호
│   ├── SajuCalculator.java      생년월일시 → 팔자 (순수)
│   ├── ReadingScorer.java       팔자 → 등급 (순수, 결정적)
│   ├── ReadingGenerator.java    등급 → 보살 톤 문장 (LLM)
│   ├── ReadingCategory.java
│   └── dto/
│
├── result/                      AGENTS.md ○   최선우
│   ├── ResultController/Service/Repository
│   ├── ReadingRepository
│   ├── entity/  Result, Reading
│   └── dto/
│
├── compatibility/               AGENTS.md ○   최선우
│   ├── CompatibilityController/Service/Repository
│   ├── CompatibilityCalculator  (순수)
│   ├── entity/  Compatibility
│   ├── CompatibilityTier.java
│   └── dto/
│
├── auth/                        곽도윤   ← 카카오 로그인 (2026-09-21, 구현 전)
│   ├── AuthController           POST /api/auth/kakao
│   ├── AuthService              code 교환 → member upsert → 결과 연결·복원(§4) → JWT 발급
│   ├── KakaoClient              토큰·유저 정보 조회 (RestClient, 타임아웃 명시)
│   ├── JwtProvider              발급·검증 (HS256, 알고리즘 고정)
│   └── JwtInterceptor           인증 경로 검증 + @CurrentMember 인자 주입
│
├── member/                      곽도윤   ← 구현 전
│   ├── MeController             GET /api/me, GET /api/me/result (결과 조회는 ResultService 에 위임)
│   ├── MemberRepository
│   ├── entity/  Member
│   └── dto/
│
├── wallet/                      담당 미정 ← plan §1.4·§9.4 실·원장·출석 (소개팅 BE 와 함께)
├── dating/                      담당 미정 ← plan §7 프로필·추천·해금·요청 (명세 확정 후). signup/ 을 대체한다
│
└── signup/                      AGENTS.md ○   곽도윤
    ├── SignupController/Service/Repository
    ├── EmailVerificationService
    ├── entity/  Signup, EmailVerification
    └── dto/
```

### 담당과 1주 핵심 과제

| 담당 | 패키지 | 핵심 과제 |
|---|---|---|
| **차은호** | `saju/` | 만세력 라이브러리 선정·연동, 등급 로직, 프롬프트 |
| **최선우** | `result/`, `compatibility/` | FE 연동 API, 궁합, 캐싱 |
| **곽도윤** | `signup/`, `common/`, `auth/`, `member/`, 인프라 | 메일·인증, Docker·CI·EC2·보안, 카카오 로그인·JWT (`auth/`·`member/` 담당은 제안, §9) |

**Day 1에 곽도윤이 배포 파이프라인을 먼저 뚫는다.** 빈 화면이라도 실제 도메인에 떠 있어야
마지막 날이 배포 삽질로 사라지지 않는다.

### 의존 방향

```
signup  →  result       허용 (단방향)
result  →  saju         허용
result  →  signup       금지
saju    →  result       금지
saju    →  (스프링 컨텍스트)  금지
auth    →  member       허용
auth    →  result       허용 (로그인 시 결과 연결·복원)
member  →  result       허용 (내 결과 조회, `GET /api/me/result`)
result  →  member       금지 (`Result` 는 `Long memberId` 컬럼만 가진다. 엔티티·패키지 참조 없음)
member  →  auth         금지
dating  →  result·compatibility·member·wallet   허용
compatibility  →  wallet   허용 (친구 궁합 등록 시 공유자에게 실 +3)
wallet  →  (다른 도메인)  금지 (원장이 가장 아래)
```

`saju/` 는 순수 계산 모듈로 유지한다. DB 없이 단위 테스트가 가능해야 한다.

---

## 4. 인증 설계

| 파트 | 방식 |
|---|---|
| 사주·궁합 | **인증 없음.** 로그인 여부와 무관하게 전 기능 사용 가능. 본인용 `resultId`, 공개 링크용 `shareId` 사용 |
| 로그인 후 내 사주 복원 (선택) | 카카오 로그인 시 브라우저의 `resultId` 를 계정에 연결하고, 계정에 이미 결과가 있으면 계정 결과를 복원한다 (plan §1.1). **브라우저 저장소를 잃어도 내 결과와 궁합 지도를 다시 찾기 위한 것.** 로그인하지 않아도 사주 기능은 그대로 동작 |
| 소개팅 | **카카오 로그인 필수 + 학교 메일 재학 인증.** JWT 로 인증 |
| 사전등록 (파일럿 API) | 학교 이메일 **매직링크**. V1 에서 소개팅 프로필(`dating/`)이 대체할 때까지 익명 유지 |

### UUID 링크 키를 쓰는 이유

- QR 진입 후 회원가입 화면이 뜨면 절반이 이탈한다
- 익명이어야 개인정보 부담이 없다
- 친구 궁합은 "링크를 아는 사람"만 참여하면 되므로 URL 소유 = 권한으로 충분

**보안 요구사항**: `resultId`와 `shareId`는 반드시 **UUIDv4**.
순번이면 남의 사주 결과를 전부 긁을 수 있다.

**예외 (2026-09-21 수용)**: `compatibility.id` 는 순번(BIGSERIAL)이고 `GET /api/compatibilities/{id}/reason` 에 그대로 노출된다.
열거하면 남의 궁합 이유를 읽거나 LLM 생성을 유발할 수 있다. 호출 총량은 `CallBudget` 이 막는다(초과 시 `LLM_UNAVAILABLE`).

### 매직링크

```
신청 → 토큰 생성(SecureRandom) → email_verification 저장(TTL 30분) → 메일 발송
     → 클릭 → expires_at > now() AND used_at IS NULL 검증
     → verified_at 기록 + used_at 기록 → 프론트 완료 페이지로 302
```

매직링크는 토큰 조회 후 리다이렉트가 전부다. V1 에서는 이 방식을 **소개팅의 학교 메일 재학 인증**으로 재사용한다.
메일 링크는 다른 브라우저·메일 앱에서 열리므로 `Authorization` 헤더가 없다. 토큰이 곧 자격이라 `verify` 는 인증 없이 열려 있다.
인증 시점·저장 위치·이후 게이트는 미정이다 (plan.md TBD-16).

### 카카오 로그인 (2026-09-21 확정 · 구현 전)

기획 원본은 `docs/plan.md` §1.1·§1.3·§8.1. **프론트가 카카오 인가를 진행하고**, 받은 `code` 를 백엔드로 넘긴다.

```
프론트: 카카오 인가 → redirectUri(프론트 콜백)로 code 수신
  → POST /api/auth/kakao {code, redirectUri, resultId?, ref?}
백엔드: redirectUri 화이트리스트 검증 → 카카오 토큰 교환(client secret) → 유저 정보(id 만)
  → member upsert(kakao_id) → 결과 연결·복원(plan §1.1) → JWT 발급
응답:   Set-Cookie: wks_token(HttpOnly·Secure·SameSite=Lax) + body {isNewUser, restoredResultId, rewardGranted}
이후:   브라우저가 쿠키를 자동으로 실어 보낸다 (credentials: 'include' 필요) — /api/me, /api/dating/**, /api/wallet/**
```

**2026-09-25, 토큰 전달 방식을 `Authorization: Bearer` 헤더에서 HttpOnly 쿠키로 전환했다** (의도적 결정,
`docs/handoff.md` 참고). 서버 쪽 세션 저장소나 Spring Security 를 들이는 건 아니다 — JWT 를 담는
그릇만 바뀌었다. 로그아웃도 이제 `POST /api/auth/logout` 이 쿠키를 지운다(과거엔 프론트가 로컬 토큰을
지우는 방식뿐이었다).

**설계 원칙**

1. **사주는 로그인을 요구하지 않는다.** JWT 검증은 인증 경로에만 건다. 사주·궁합·공유 API 는 쿠키를 읽지 않는다. 익명 API 가 회원을 알아야 하는 경우(plan §5.8 중복 등록 방지)는 `TBD-14` 결정 전까지 구현하지 않는다.
2. **결과와 계정은 `result.member_id` 로 연결한다.** nullable, 계정당 결과 1개(부분 unique). 연결 규칙은 plan §1.1(계정 결과 우선, 브라우저 결과는 삭제·병합하지 않음)이고, 로그인 요청에 `resultId` 가 실렸을 때만 연결한다. `Result` 는 `Long memberId` 만 가지며 `member` 패키지를 참조하지 않는다. 로그인한 클라이언트는 `resultId` 를 저장해 두지 않아도 `GET /api/me/result` 로 내 결과를 받는다.
3. **카카오 프로필을 저장하지 않는다.** 동의항목 없이 `id` 만 쓰고 `member` 는 `kakao_id` 만 가진다. `kakao_id` UNIQUE 가 중복 계정·중복 신청을 막는다.
4. **JWT.** HS256, 서명키는 환경변수, 알고리즘을 고정하고(헤더의 `alg` 를 믿지 않는다), 클레임은 `sub`(memberId)·`iat`·`exp` 만 담는다. 만료 15일·갱신 없음(2026-09-21 결정, 2026-09-23 30일→15일로 조정)이고 만료되면 재로그인한다. **서버 쪽 토큰 폐기·기기 관리는 하지 않는다(2026-09-21 결정 유지).** 로그아웃은 `POST /api/auth/logout` 이 쿠키를 지운다(2026-09-25, 이전엔 프론트가 로컬 토큰을 지우는 방식뿐이었다) — 그 전에 탈취된 사본은 만료까지 그대로 유효하다. 서명키를 바꾸면 전원이 로그아웃된다. **토큰은 HttpOnly 쿠키로 내려가 JS 가 값을 읽을 수 없다(2026-09-25, XSS 노출 완화) — CSRF 는 `SameSite=Lax` + 상태변경 API 는 전부 POST/PATCH 로 막는다(별도 CSRF 토큰 없음).**
5. **카카오 access token 은 저장하지 않는다.** 로그인 이후 카카오를 다시 호출하지 않는다. 카카오 외부 호출은 타임아웃을 명시한다 (NFR-P-04).
6. **`redirectUri` 는 화이트리스트 값만 허용한다.** 클라이언트가 보낸 값을 그대로 카카오에 넘기지 않는다.
7. **잘못된 `ref`(제휴 코드)는 조용히 무시하고 로그인은 성공한다** (plan §8.1).
8. **쿠키는 host-only 로 발급한다 (`Domain` 속성을 안 준다, 2026-09-25).** 운영(`api.threadoffate.site`)·개발(`api-dev.threadoffate.site`) 쿠키가 서로 안 섞인다. CORS 는 `WebConfig` 에 `allowCredentials(true)` 가 있어야 브라우저가 쿠키를 실어 보낸다 — `allowedOrigins` 와일드카드는 credentials 모드에서 애초에 금지돼 있다.
9. **탈퇴는 V1 범위 밖이다 (2026-09-21 결정).** 개인정보 삭제 요청은 기능이 아니라 운영자가 직접 처리한다. 요청 창구를 처리방침에 명시해야 한다 (§9). **로그아웃은 있다 (2026-09-25, `POST /api/auth/logout`)** — 쿠키만 지우고 서버 쪽 토큰 무효화는 여전히 없다.

| API (구현 전, 초안은 `docs/api-spec.md` §9) | 동작 |
|---|---|
| `POST /api/auth/kakao` | code 교환 → member upsert → 결과 연결·복원 → JWT 발급, 쿠키로 내려줌 (익명) |
| `POST /api/auth/logout` | 로그인 쿠키를 지운다 (익명, 2026-09-25 추가) |
| `GET /api/me` | 인증 필요. 로그인 상태, 결과·프로필 보유 여부, 실 잔액 |
| `GET /api/me/result` | 인증 필요. 계정에 연결된 내 결과를 `GET /api/results/{resultId}` 와 같은 구조로 반환, 없으면 404. 클라이언트가 `resultId` 를 잃어도 복원할 수 있다 |

소개팅·실 API(`/api/dating/**`, `/api/wallet/**`)는 명세가 확정되면 이 표에 옮긴다.

---

## 5. DB 스키마 (현재)

> Flyway 마이그레이션 적용 후의 기준. **엔티티가 아니라 이 문서가 원본이다.**

```sql
CREATE TABLE result (
    id              UUID PRIMARY KEY,
    share_id        UUID UNIQUE NOT NULL,
    nickname        VARCHAR(20)  NOT NULL,
    birth_date      DATE         NOT NULL,
    birth_time      TIME,                    -- NULL = 시간 모름
    birth_region    VARCHAR(50),             -- NULL = 지역 모름
    gender          VARCHAR(10)  NOT NULL,
    calendar_type   VARCHAR(10)  NOT NULL DEFAULT 'SOLAR',  -- 아래 3개는 입력 폼 자동 채움용 원본 입력값 (#66)
    birth_date_input VARCHAR(10),                           -- 입력한 날짜 문자열. LUNAR 면 음력 (birth_date 는 양력 변환값)
    is_leap_month   BOOLEAN      NOT NULL DEFAULT false,
    year_pillar     CHAR(2)      NOT NULL,
    month_pillar    CHAR(2)      NOT NULL,
    day_pillar      CHAR(2)      NOT NULL,
    hour_pillar     CHAR(2),                 -- 시간 모르면 NULL
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE reading (
    result_id          UUID         PRIMARY KEY REFERENCES result(id) ON DELETE CASCADE,
    destiny_content    TEXT         NOT NULL,   -- 운명 제목은 저장하지 않는다. 점수 상/하 조합 8종으로 조회 시 계산 (saju/DestinyTitle)
    marriage_score     SMALLINT     NOT NULL,   -- 0~100. 등급은 응답 시 Grade.of(score)
    marriage_content   TEXT         NOT NULL,
    children_score     SMALLINT     NOT NULL,
    children_content   TEXT         NOT NULL,
    love_score         SMALLINT     NOT NULL,
    love_content       TEXT         NOT NULL,
    version            INTEGER      NOT NULL DEFAULT 0,  -- 프롬프트·점수 로직 버전. 같은 입력 해석 재사용은 같은 버전끼리만 (#62). 0 = 도입 전 행
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
    -- 행운 아이템·장소는 저장하지 않는다. 아이템은 조회 시 팔자 + 오늘 일진 + 생년월일·시간·성별 (saju/DailyLucky), 장소는 원국 강약 (saju/LuckyPlace)
);

CREATE TABLE compatibility (
    id              BIGSERIAL PRIMARY KEY,
    origin_id       UUID         NOT NULL REFERENCES result(id) ON DELETE CASCADE,
    guest_id        UUID         NOT NULL REFERENCES result(id) ON DELETE CASCADE,
    score           SMALLINT     NOT NULL,
    tier            VARCHAR(10)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (origin_id, guest_id)
);

CREATE INDEX idx_compat_origin ON compatibility(origin_id);

CREATE TABLE signup (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    result_id       UUID         REFERENCES result(id) ON DELETE SET NULL,
    gender          VARCHAR(10)  NOT NULL,
    prefer_gender   VARCHAR(10)  NOT NULL,
    coupon_issued   BOOLEAN      NOT NULL DEFAULT false,
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE email_verification (
    token           VARCHAR(64)  PRIMARY KEY,
    signup_id       BIGINT       NOT NULL REFERENCES signup(id) ON DELETE CASCADE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_signup ON email_verification(signup_id);

-- 카카오 로그인 (2026-09-21 확정, 구현 전 — V11). 카카오 프로필(닉네임·이메일 등)은 저장하지 않는다
CREATE TABLE member (
    id              BIGSERIAL PRIMARY KEY,           -- 외부 노출 안 함. resultId 와 달리 UUID 불필요
    kakao_id        BIGINT       NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 계정과 결과 연결 (plan §1.1). 계정당 결과 1개. 회원이 없어지면 결과는 익명으로 남는다
ALTER TABLE result ADD COLUMN member_id BIGINT REFERENCES member(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_result_member ON result(member_id) WHERE member_id IS NOT NULL;

-- 실 원장 (plan §1.4·§9.4, V12 예약 — 소개팅 BE 와 함께). 모든 증감을 기록하고 잔액은 합계로 계산한다
CREATE TABLE thread_ledger (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT       NOT NULL REFERENCES member(id),
    amount          INTEGER      NOT NULL,           -- 획득 +, 소모 -
    reason          VARCHAR(20)  NOT NULL,           -- SIGNUP_BONUS | CHECK_IN | MAP_FRIEND | PARTNER | UNLOCK | REQUEST | REROLL
    ref_id          VARCHAR(64)  NOT NULL,           -- NULL 금지: NULL 이면 unique 가 중복을 못 막는다 (Postgres 는 NULL 끼리 다르다고 본다)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (member_id, reason, ref_id)
);

-- 소개팅 프로필·추천·요청은 V15~V17, 소개팅 궁합 이유 캐시는 V18에 추가한다. 해금·실 원장은 후속 작업이다 (plan.md §7)
```

### 스키마 규칙

- 시각은 전부 `TIMESTAMPTZ`. `TIMESTAMP` 금지 (배포 환경 타임존 사고 방지)
- enum은 DB에 `VARCHAR`, 애플리케이션에서 Java enum (`@Enumerated(EnumType.STRING)`)
- **`name`·`phone` 컬럼은 없다. 추가하지 마라**
- **`member` 에 프로필 컬럼(닉네임·이메일·이름·프로필 사진)을 추가하지 마라.** 로그인 식별자는 `kakao_id` 하나다
- **한 계정에 결과를 둘 이상 연결하지 마라.** `result.member_id` 는 계정당 1개(부분 unique)다. 병합은 V2 (plan §1.1)
- **`thread_ledger.ref_id` 는 NOT NULL.** reason 별 값 규칙: `SIGNUP_BONUS` = member id, `CHECK_IN` = KST 날짜(`yyyy-MM-dd`), `MAP_FRIEND` = compatibility id, `PARTNER` = 제휴 코드. `UNLOCK`·`REQUEST`·`REROLL` 은 소개팅 명세 확정 시 정한다

---

## 6. 사주 계산 파이프라인

```
CreateResultRequest
  → ReadingRepository   같은 생년월일·시간·성별 + 같은 버전의 해석이 있으면 복사하고 아래 분석은 건너뜀 (#62)
  → KoreanLunarCalendar 음력 입력이면 양력으로 변환 (KASI 표)
  → SajuCalculator      절기·진태양시 보정 → 팔자 4주 (lunar-java)
  → ReadingScorer       결혼·자녀·연애 점수 산출 (성별로 배우자성·자녀성 결정)   ← 결정적
  → ReadingGenerator    등급+팔자+성별+잘 맞는 오행 → 보살 톤 문장   ← LLM (운명 설명·결혼·자녀·연애·잘 맞는 오행 이유 문장만, 한 번에)
  → DestinyTitle        조회 시: 점수 상/하 조합 → 운명 제목 8종   ← 코드 표
  → ReadingRepository   저장
  → DailyLucky          조회 시: 오행별 (궁합 40% + 오늘 일진 활성도 60%) → 행운 오행 → 아이템   ← 코드, 매일 변경
  → LuckyPlace          조회 시: 원국 오행 세력·신강/신약 → 보완 오행(고정) → 풀에서 날짜별 장소   ← 코드, 매일 변경
  → ElementMatch        조회 시: 같은 보완 오행 = "나와 잘 맞는 오행" (기능명세 3.5). 이유 문장은 reading.element_match_content   ← 오행은 코드, 문장은 LLM
```

**등급은 코드가, 문장은 AI가.** 이 경계가 흐려지면:
같은 사주에 다른 등급이 나와 신뢰가 깨지고, 테스트가 불가능해지고, 비용과 지연이 늘어난다.

### 캐싱

`reading.result_id`가 PK이므로 Result당 해석은 한 행만 저장한다.
값이 있으면 **LLM을 호출하지 않는다.** 예외 없음.

### 궁합 이유 (plan §1.2 · #79 #80)

- **등록 시가 아니라 처음 열어볼 때 생성**한다 (`GET /api/compatibilities/{id}/reason`, `CompatibilityReasonService`). 공유가 몰릴 때 등록 즉시 생성하면 429 로 죽는다
- 생성 결과는 `compatibility.reason_*` 세 컬럼(V13)에 캐싱하고, 재조회는 LLM 호출 0회다. 조합은 A↔B 무순서로 한 행이라 **두 사람이 같은 글을 본다**
- 세 질문("왜 귀인인가"·"둘이 만나게 된다면"·"둘이 싸움이 난다면")을 **한 번의 호출**로 생성한다 (FR-GM-02). `saju/CompatibilityReasonGenerator` + `prompts/compatibility-reason-system.txt`
- 실패하면 `LLM_UNAVAILABLE` 503. 프론트는 해당 영역만 미노출하고 재시도할 수 있다. 화면 전체를 에러로 만들지 않는다
- 프롬프트에는 팔자·점수·관계유형과 코드가 정한 오행 사실(두 기운·상생/상극)만 넣는다. **성별은 넣지 않는다** — 성별을 고려한 글은 소개팅 쪽이 따로 만든다. 호출은 `saju/GeminiJson` 의 `CallBudget` 하나에 사주 해석과 함께 집계된다
- 동시에 처음 열면 LLM 을 두 번 부를 수 있지만 저장은 `UPDATE … WHERE reason_why IS NULL` 로 먼저 온 쪽만 되고, 진 쪽은 저장된 글을 다시 읽는다. 잠금은 LLM 30초 동안 DB 연결을 잡아 두므로 쓰지 않는다
- 서비스 메서드에 트랜잭션을 걸지 않는다. 같은 이유
- 프롬프트를 바꾸면 기존 캐시는 옛 글로 남는다. 다시 만들려면 `UPDATE compatibility SET reason_why = NULL, reason_together = NULL, reason_conflict = NULL` (버전 컬럼은 두지 않았다)
- 소개팅 "궁합 까닭"은 친구 궁합과 다른 문구다. 첫 REASON 해금 성공 시 생성하고 `dating_recommendation.reason_content`(V18)에 캐싱한다. 추천 조회에서는 LLM을 호출하지 않는다

### 시간·지역 모름

- `birth_time` NULL → 시주 생략, 3주로 해석. 응답에 `hourUnknown: true`
- 출생 지역은 받지 않는다(9/13 기획 결정, 시진 단위 입력이라 무의미). `birth_region` 은 항상 NULL 이고 서울 경도(126.978°) 기준으로 진태양시 보정한다. `SajuCalculator` 는 지역 파라미터를 남겨 두었으므로 분 단위 입력으로 바뀌면 그대로 살릴 수 있다
- `birth_time` NULL → 정오 기준으로 연·월·일주 판정
- 진태양시 23시(자시 시작) 이후는 다음날 일주·시주로 본다 — 포스텔러 기본값과 동일. 균시차·과거 표준시·서머타임은 보정하지 않는다

### 만세력 — lunar-java

`cn.6tail:lunar` 는 **GMT+8 벽시계 기준**이다. 절기(연주·월주)는 KST−1h 로 넣어 판정하고,
일주·시주는 출생지 진태양시로 따로 계산한다. **음력은 중국 기준이라 쓰지 않는다** —
한국 음력 변환은 `KoreanLunarCalendar`(KASI 표, 1940~2030) 가 맡는다.

### LLM 연동 — Gemini Flash 무료 티어

SDK: `com.google.genai:google-genai` (Gemini Developer API).
API 키는 Google AI Studio에서 발급. 환경변수 `GOOGLE_API_KEY`.
모델 `gemini-3.5-flash-lite` (설정 `gemini.model`), 타임아웃 30초 (`gemini.timeout-seconds`).
`gemini-2.5-flash` 는 2026-09 기준 신규 키에 404 ("no longer available to new users").
모델별 무료 한도가 크게 다르다: `gemini-3.6-flash` 는 하루 20건, `gemini-3.5-flash-lite` 는 하루 2,000건 이상 실측(2026-09-13, 1토큰 요청). 실제 크기 요청(약 2,100토큰/건)으로도 분당 90건·20만 토큰까지 429 없음 (2026-09-16, p95 5.8초). 그래서 lite 를 기본으로 쓴다.
3.x 는 `thinkingBudget` 을 거부하므로 `thinkingLevel: MINIMAL` 로 사고 토큰을 줄인다.

**호출 총량 상한** (`saju/CallBudget`, #64): 분당 `gemini.max-per-minute`(기본 60)·일일 `gemini.max-per-day`(기본 1,600) 넘으면 Gemini 를 부르지 않고 `LLM_UNAVAILABLE`. 무료 한도는 키가 아니라 **프로젝트 단위**이고 **태평양 자정에 리셋**(KST 16:00, 서머타임 땐 17:00)이라 일일 창도 그 기준. 재시도도 한도를 쓰니 시도마다 센다. 같은 입력 재사용(#62)은 카운트 안 함. 메모리 카운터라 재시작하면 0 부터. IP 제한은 축제장 NAT 때문에 좁게 못 잡아서 총량으로 지킨다 (nginx `limit_req` 는 폭주 차단용으로 넓게, 곽도윤).

> SDK 2.0.0부터 Java 17 이상이 필수다. 우리는 Java 17이라 문제없다.

**제약 1 — 프롬프트에 개인정보를 넣지 않는다.**

무료 티어에서는 프롬프트와 응답이 Google 제품 개선에 사용될 수 있다(사람 검토 포함).
유료 티어는 사용되지 않는다.

우리 파이프라인은 이미 팔자를 코드로 계산하므로, **LLM에는 팔자와 등급만 보낸다.**
생년월일·시간·지역·닉네임을 프롬프트에 넣지 마라. 팔자(`갑진`, `계묘`)만으로는 역산이 사실상 불가능하다.

```
✗ "2002년 3월 14일 14시 30분 서울 출생 남성 도윤의 연애운"
○ "년주 임오 월주 계묘 일주 갑진 시주 신미, 연애운 78점. 보살 말투로 3문장."
```

이렇게 하면 개인정보가 외부로 나가지 않으므로 무료 티어를 써도 된다.

**제약 2 — Rate limit이 진짜 위험이다.**

무료 티어는 분당 요청 수(RPM)와 일일 요청 수(RPD)가 제한되고, 초과 시
`429 RESOURCE_EXHAUSTED` 가 떨어진다. 축제 트래픽은 평시 0에서 한순간에 몰리는 형태라
여기서 터질 가능성이 높다.

대응:
- 캐싱이 1차 방어선이다. `reading.result_id` PK로 재호출을 원천 차단
- **운명 콘텐츠를 항목별로 개별 호출하지 말고, 한 번의 호출로 전부 생성**한다.
  호출 수가 1/5로 줄어든다. JSON으로 받아 파싱
- 429를 `LLM_UNAVAILABLE` 503으로 변환하고, 팔자는 저장해 재시도 가능하게
- Day 6 부하 테스트에서 **실제 RPM 한도를 확인**한다. 모델·시점에 따라 다르다
- 한도가 부족하면 결제 계정을 연결한다. 무료 쿼터를 쓰면서 RPM만 올라간다

> EEA·스위스·영국 사용자를 대상으로 하는 서비스는 유료 티어만 써야 한다는 약관 조항이 있다.
> 국내 축제 대상이라 해당 없지만, 교환학생 등 대상이 넓어지면 확인이 필요하다.

---

## 7. 궁합 점수 구간

| 점수 | Tier | 표기 |
|---|---|---|
| 90~100 | `GUIIN` | 귀인 |
| 75~89 | `CHALTTEOK` | 찰떡 |
| 61~74 | `BEOT` | 벗 |
| 0~60 | `SEUCHIM` | 스침 |

2026-09-13 기획 확정. 25점 균등 구간이 아니다.

실제 팔자 10만 무작위 조합의 원점수 누적 경계(50/60/70)를 Tier 경계(61/75/90)에 맞춰
단조 구간 보정한다. 목표 분포는 귀인 20% · 찰떡 30% · 벗 30% · 스침 20%이며 관계 점수의 순서는 유지한다.

`CompatibilityCalculator` 는 순수 함수. `score(A,B) == score(B,A)` 가 성립해야 한다.

---

## 8. 부하 특성

평시 거의 0 → **공유 링크가 퍼지는 순간 급증.**

- `open-in-view: false` — 기본값 true라 뷰 렌더링까지 커넥션을 잡고 있어 풀이 먼저 마른다
- LLM 호출은 캐시로 최소화 (비용·지연 양쪽)
- `GET /api/results/{id}` 가 공유 링크 주 진입점이라 가장 많이 호출된다
- 사주·궁합 익명 엔드포인트는 인증 처리를 거치지 않는다 (§4). 궁합 이유는 최초 열람 때 LLM 을 부르므로 공유가 몰리면 429 위험이 있다 — 그래서 열람 시 생성하고 캐시한다
- 축제 기간만 EC2 스펙 업

부하 테스트는 이 엔드포인트 집중 호출만 검증하면 된다.

---

## 9. 미결정 사항

| 항목 | 시점 | 담당 |
|---|---|---|
| Gemini 무료 티어 RPM·RPD 실측 | Day 6 | 차은호 |
| 무료 티어 한도 부족 시 결제 계정 연결 여부 | Day 6 | 곽도윤 |
| 프롬프트 톤 확정 | Day 2 밤 | 차은호 + 기획 |
| 프론트 배포 도메인 (CORS) | Day 1 | 곽도윤 |
| 학교 웹메일 도메인 화이트리스트 | Day 3 | 곽도윤 |
| SMTP 발송 계정 (한도 확인) | Day 3 | 곽도윤 |
| 없는 `resultId` 로 신청 시 처리 방침 | Day 3 | 곽도윤 |
| 데이터 파기 스크립트 | Day 5 | 곽도윤 |
| JWT 라이브러리 도입 팀 승인 (convention: 새 라이브러리는 팀 합의). Nimbus JOSE+JWT / jjwt, Boot 4 의 Jackson 3 과 공존 여부 확인 | 로그인 구현 전 | 곽도윤 + 팀 |
| 카카오 디벨로퍼스 앱 설정: 운영·개발 앱, redirect URI(**프론트 콜백 주소**), client secret, 동의항목 없이 진행 가능한지 | 로그인 구현 전 | 곽도윤 |
| 파일럿 `signup/`·`/api/signups` 제거 시점과 기존 신청 데이터 처리 (소개팅 프로필이 대체) | 소개팅 BE 1차 이후 | 곽도윤 |
| `auth/`·`member/`·`wallet/`·`dating/` 담당 확정 | 로그인 구현 전 | 팀 |
| 학교 메일 인증의 위치·저장 위치·게이트, 소개팅 프로필 성별·선호성별 (plan.md TBD-14~16) | 소개팅 BE 착수 전 (09/22) | 기획 + 곽도윤 |
| 데이터 파기 범위: 회원·소개팅 프로필·사진·실 원장 (NFR-S-07, 축제 종료 10/1 + 2주 = 10/15) | 소개팅 BE 전 | 곽도윤 + 기획 |
| 개인정보 처리방침·동의 문구 개정: 카카오 ID 수집, 결과-계정 연결 고지, 소개팅 프로필·사진, **탈퇴 기능이 없으므로 삭제 요청 창구** | 배포 전 | 기획 + 곽도윤 |

**결정되면 표에서 지우고 본문에 반영한다.** 미결정으로 남겨두지 않는다.

> 만세력은 **직접 구현하지 않는다.** 절기 경계·진태양시·야자시로 일주일이 그냥 간다.
> 라이브러리를 못 찾으면 1950~2010 범위 24절기 시각을 리소스 테이블로 박아 우회한다.

# architecture.md

백엔드 구조와 설계 결정. **이 문서가 코드보다 우선한다.**

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
        └─▶ SMTP (인증 메일)
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
| LLM | **Gemini Flash** via `com.google.genai:google-genai` (무료 티어) |
| 만세력 | `cn.6tail:lunar` (MIT) + 한국 음력표 `saju/KoreanLunarCalendar` |
| API Docs | **springdoc-openapi 3.1.1** |
| Monitoring | Actuator |
| Test | JUnit5, Mockito, Testcontainers |
| Infra | Docker, EC2, nginx, GitHub Actions |

### 의도적으로 뺀 것

| 항목 | 이유 | 넣을 시점 |
|---|---|---|
| Spring Security | 인증 체인이 없다. 매직링크는 토큰 조회 후 리다이렉트가 전부 | 2차 세션 도입 시 |
| Redis | 토큰 TTL은 `expires_at` 컬럼으로 충분. 컨테이너를 늘리지 않는다 | 조회 1초 초과 또는 풀 고갈 시 |
| JWT | 서버 상태 없이 검증하려는 것인데, 어차피 "사용 후 삭제"라 상태를 든다. 무효화만 까다로워짐 | 2차 |
| QueryDSL | 설정 비용이 1주 일정에 안 맞는다 | 복잡 쿼리 발생 시 |
| H2 | PostgreSQL과 문법이 달라 로컬 통과/배포 실패가 난다 | 없음 |

### Spring Boot 4 주의사항

- Spring Framework 7 / Jakarta EE 11 / Servlet 6.1 베이스라인
- **Jackson 3**. Jackson 2 전용 API를 쓰면 깨진다
- Boot 3에서 deprecated였던 API는 **제거**됐다
- **springdoc은 반드시 3.x.** 2.x는 기동에 실패한다
- AI 도구의 학습 데이터 대부분이 Boot 3 기준이다. 생성 결과를 검증할 것

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
| **곽도윤** | `signup/`, `common/`, 인프라 | 메일·인증, Docker·CI·EC2·보안 |

**Day 1에 곽도윤이 배포 파이프라인을 먼저 뚫는다.** 빈 화면이라도 실제 도메인에 떠 있어야
마지막 날이 배포 삽질로 사라지지 않는다.

### 의존 방향

```
signup  →  result       허용 (단방향)
result  →  saju         허용
result  →  signup       금지
saju    →  result       금지
saju    →  (스프링 컨텍스트)  금지
```

`saju/` 는 순수 계산 모듈로 유지한다. DB 없이 단위 테스트가 가능해야 한다.

---

## 4. 인증 설계

| 파트 | 방식 |
|---|---|
| 사주·궁합 | **인증 없음.** `resultId`(UUIDv4)가 곧 열쇠 |
| 사전등록 | 학교 이메일 **매직링크**. 비밀번호·세션 없음 |

### resultId를 열쇠로 쓰는 이유

- QR 진입 후 회원가입 화면이 뜨면 절반이 이탈한다
- 익명이어야 개인정보 부담이 없다
- 친구 궁합은 "링크를 아는 사람"만 참여하면 되므로 URL 소유 = 권한으로 충분

**보안 요구사항**: `resultId` 는 반드시 **UUIDv4**.
순번이면 남의 사주 결과를 전부 긁을 수 있다.

### 매직링크

```
신청 → 토큰 생성(SecureRandom) → email_verification 저장(TTL 30분) → 메일 발송
     → 클릭 → expires_at > now() AND used_at IS NULL 검증
     → verified_at 기록 + used_at 기록 → 프론트 완료 페이지로 302
```

Spring Security가 필요 없는 이유가 여기 있다. **인증 체인도 세션도 없다.**
토큰 조회 후 리다이렉트가 전부다.

---

## 5. DB 스키마 (현재)

> Flyway 마이그레이션 적용 후의 기준. **엔티티가 아니라 이 문서가 원본이다.**

```sql
CREATE TABLE result (
    id              UUID PRIMARY KEY,
    nickname        VARCHAR(20)  NOT NULL,
    birth_date      DATE         NOT NULL,
    birth_time      TIME,                    -- NULL = 시간 모름
    birth_region    VARCHAR(50),             -- NULL = 지역 모름
    gender          VARCHAR(10)  NOT NULL,
    year_pillar     CHAR(2)      NOT NULL,
    month_pillar    CHAR(2)      NOT NULL,
    day_pillar      CHAR(2)      NOT NULL,
    hour_pillar     CHAR(2),                 -- 시간 모르면 NULL
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE reading (
    result_id          UUID         PRIMARY KEY REFERENCES result(id) ON DELETE CASCADE,
    destiny_title      VARCHAR(100) NOT NULL,
    destiny_content    TEXT         NOT NULL,
    marriage_score     SMALLINT     NOT NULL,   -- 0~100. 등급은 응답 시 Grade.of(score)
    marriage_content   TEXT         NOT NULL,
    children_score     SMALLINT     NOT NULL,
    children_content   TEXT         NOT NULL,
    love_score         SMALLINT     NOT NULL,
    love_content       TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
    -- 행운 아이템·장소는 저장하지 않는다. 조회 시 팔자 + 오늘 일진으로 계산 (saju/DailyLucky)
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
```

### 스키마 규칙

- 시각은 전부 `TIMESTAMPTZ`. `TIMESTAMP` 금지 (배포 환경 타임존 사고 방지)
- enum은 DB에 `VARCHAR`, 애플리케이션에서 Java enum (`@Enumerated(EnumType.STRING)`)
- **`name`·`phone` 컬럼은 없다. 추가하지 마라**

---

## 6. 사주 계산 파이프라인

```
CreateResultRequest
  → KoreanLunarCalendar 음력 입력이면 양력으로 변환 (KASI 표)
  → SajuCalculator      절기·진태양시 보정 → 팔자 4주 (lunar-java)
  → ReadingScorer       결혼·자녀·연애 등급 산출     ← 결정적
  → ReadingGenerator    등급+팔자 → 보살 톤 문장     ← LLM (운명·결혼·자녀·연애 문장만)
  → ReadingRepository   저장
  → DailyLucky          조회 시: 팔자 + 오늘 일진 → 오늘의 행운 오행·아이템·장소   ← 코드, 매일 변경
```

**등급은 코드가, 문장은 AI가.** 이 경계가 흐려지면:
같은 사주에 다른 등급이 나와 신뢰가 깨지고, 테스트가 불가능해지고, 비용과 지연이 늘어난다.

### 캐싱

`reading.result_id`가 PK이므로 Result당 해석은 한 행만 저장한다.
값이 있으면 **LLM을 호출하지 않는다.** 예외 없음.

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
모델 `gemini-3.6-flash` (설정 `gemini.model`), 타임아웃 30초 (`gemini.timeout-seconds`).
`gemini-2.5-flash` 는 2026-09 기준 신규 키에 404 ("no longer available to new users").
3.x 는 `thinkingBudget` 을 거부하므로 `thinkingLevel: MINIMAL` 로 사고 토큰을 줄인다.

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

`CompatibilityCalculator` 는 순수 함수. `score(A,B) == score(B,A)` 가 성립해야 한다.

---

## 8. 부하 특성

평시 거의 0 → **공유 링크가 퍼지는 순간 급증.**

- `open-in-view: false` — 기본값 true라 뷰 렌더링까지 커넥션을 잡고 있어 풀이 먼저 마른다
- LLM 호출은 캐시로 최소화 (비용·지연 양쪽)
- `GET /api/results/{id}` 가 공유 링크 주 진입점이라 가장 많이 호출된다
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
| 축제 D-day / 선릴리즈 일자 | 즉시 | 곽도윤 |

**결정되면 표에서 지우고 본문에 반영한다.** 미결정으로 남겨두지 않는다.

> 만세력은 **직접 구현하지 않는다.** 절기 경계·진태양시·야자시로 일주일이 그냥 간다.
> 라이브러리를 못 찾으면 1950~2010 범위 24절기 시각을 리소스 테이블로 박아 우회한다.

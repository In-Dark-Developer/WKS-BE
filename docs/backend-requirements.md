# 1차 릴리즈 백엔드 요구사항 명세

> **대상**: 백엔드 3명 (차은호 · 최선우 · 곽도윤)
> **범위**: 사주 계산·해석·공유·친구궁합 + 소개팅 사전등록(이메일 인증)
> **관련 문서**: `product-plan.md` · `AGENTS.md` · `docs/architecture.md` · `docs/api-spec.md`
>
> 최종 수정: 2026-09-11

---

## 1. 사용법

- 모든 요구사항에 **ID · 우선순위 · 담당 · 인수 조건**이 붙어 있다
- **인수 조건은 테스트 가능한 문장**이어야 한다. "잘 동작한다"는 인수 조건이 아니다
- 요구사항이 바뀌면 이 문서를 먼저 고치고, API 영향이 있으면 `docs/api-spec.md` 도 고쳐 **프론트 팀에 공지**한다
- Day 6 QA는 **§13 릴리즈 판정 체크리스트**로 진행한다

| 등급 | 의미 |
|---|---|
| **P0** | 없으면 릴리즈 불가 |
| **P1** | 없으면 서비스가 반쪽. 시간 부족 시 마지막까지 붙든다 |
| **P2** | 여유 있으면 |

| 담당 | 패키지 |
|---|---|
| **은호** | `saju/` — 만세력, 등급, Gemini |
| **선우** | `result/`, `compatibility/` — FE 연동 API |
| **도윤** | `signup/`, `common/`, 인프라 |

---

## 2. 시스템 책임 범위

```
프론트(별도 레포·별도 팀) ──HTTP──> [ 이 시스템 ] ──> PostgreSQL / Gemini API / SMTP
```

책임지는 것: `docs/api-spec.md` 계약 준수, 사주 계산의 정확성과 결정성,
LLM 비용·지연·한도 통제, 데이터 무결성과 개인정보 보호, 가용성과 배포

책임지지 않는 것: 화면, OG 태그, localStorage, 공유 UI

### 스택

Java 17 · Spring Boot 4.1.1 · Gradle Groovy · PostgreSQL 16 · JPA · Flyway ·
Spring Mail · springdoc-openapi 3.1.1 · Lombok · Testcontainers ·
`com.google.genai:google-genai` (Gemini Flash 무료 티어)

패키지 루트: `com.darkness.wks`

**제외**: Spring Security · Redis · JWT · QueryDSL · H2 (사유는 `docs/architecture.md` §2)

---

## 3. 릴리즈 범위

| 메서드 | 경로 | 담당 |
|---|---|---|
| `POST` | `/api/results` | 선우 (계산은 은호) |
| `GET` | `/api/results/{resultId}` | 선우 |
| `GET` | `/api/shares/{shareId}` | 선우 |
| `POST` | `/api/shares/{shareId}/compatibility` | 선우 |
| `POST` | `/api/signups` | 도윤 |
| `POST` | `/api/signups/resend` | 도윤 |
| `GET` | `/api/signups/verify` | 도윤 |
| `GET` | `/api/health` | 도윤 |

### 제외 (2차 이후)

로그인·세션 / 매칭 배치 알고리즘 / WebSocket 채팅 / 관리자 API / 결과 수정·재계산 / 푸시 알림 / 이미지 생성

---

## 4. 공통 (FR-CM)

| ID | P | 담당 | 요구사항 |
|---|---|---|---|
| FR-CM-01 | P0 | 공통 | 모든 응답은 `{success, data}` / `{success, error{code,message,traceId}}` 로 통일 |
| FR-CM-02 | P0 | 공통 | `ErrorCode` enum이 `docs/api-spec.md` 표와 **1:1 대응** |
| FR-CM-03 | P0 | 공통 | 예외는 `@RestControllerAdvice` 전역 핸들러에서 처리. Controller에 try-catch 금지 |
| FR-CM-04 | P0 | 공통 | `error.message` 는 사용자에게 그대로 노출 가능한 한국어 |
| FR-CM-05 | P0 | 공통 | 응답에 스택트레이스·SQL 오류·내부 경로 노출 금지 |
| FR-CM-06 | P0 | 도윤 | CORS를 프론트 로컬 + 배포 도메인으로 제한. **와일드카드 금지** |
| FR-CM-07 | P0 | 도윤 | `GET /api/health` 인증 없이 제공 |
| FR-CM-08 | P0 | 도윤 | 요청마다 `traceId` 발급 → MDC → 로그·에러 응답에 포함 |
| FR-CM-09 | P0 | 공통 | JSON 키는 camelCase, 시각은 ISO-8601 UTC(`Z`), **null 필드도 키 유지** |
| FR-CM-10 | P0 | 도윤 | Whitelabel 에러 페이지 비활성. 404도 우리 JSON 포맷으로 반환 |
| FR-CM-11 | P0 | 도윤 | Swagger UI 제공(`/swagger-ui.html`). 운영에서 끌 수 있게 설정값 |
| FR-CM-12 | P1 | 도윤 | Actuator는 `health` 만 노출 |

### 인수 조건

- 성공·실패 응답 모두 `success` 필드를 가진다
- 없는 URL(`/api/nope`) 호출 시 **HTML이 아니라** 우리 JSON 포맷이 나온다
- 의도적 500 유발 시 응답에 예외 클래스명·스택트레이스가 없다
- 로그의 traceId와 에러 응답의 traceId가 일치한다
- 허용되지 않은 origin에서 요청 시 CORS로 차단된다
- Swagger에서 파라미터명이 `arg0` 이 아니라 실제 이름으로 나온다 (`-parameters` 옵션)

---

## 5. 입력 검증 (FR-VL) · 선우

| ID | P | 요구사항 |
|---|---|---|
| FR-VL-01 | P0 | 서버는 모든 입력을 재검증한다. **클라이언트 검증을 신뢰하지 않는다** |
| FR-VL-02 | P0 | 검증은 DTO의 Bean Validation 애노테이션으로 선언 |
| FR-VL-03 | P0 | 검증 실패는 400 + `INVALID_INPUT` 으로 변환 |
| FR-VL-04 | P1 | 검증 실패 응답에 **어느 필드가 왜 틀렸는지** 포함 |
| FR-VL-05 | P1 | 요청 바디 크기를 제한한다 (닉네임 8자 API에 10MB가 들어올 이유가 없다) |

| 필드 | 규칙 |
|---|---|
| `nickname` | 1~8자, 공백만 불가, 필수 |
| `birthDate` | `yyyy-MM-dd`, 1950-01-01 ~ 오늘, 필수 |
| `birthTime` | `HH:mm` 또는 null(모름) |
| `birthRegion` | 최대 50자 또는 null(모름) |
| `gender` / `preferGender` | `MALE` \| `FEMALE`, 필수 |
| `email` | RFC 형식 + 도메인 화이트리스트 |

### 인수 조건

미래 날짜 → 400 · 닉네임 21자 → 400 · 닉네임 `"   "` → 400 · `gender: "OTHER"` → 400 ·
**시간·지역 모두 null → 201 정상 생성**

---

## 6. 사주 계산 (FR-SJ) · 은호

| ID | P | 요구사항 |
|---|---|---|
| FR-SJ-01 | P0 | 생년월일시·지역으로 사주 팔자 4주를 산출 |
| FR-SJ-02 | P0 | **절기 경계** 기준으로 월주를 판정. 양력 월이 아니다 |
| FR-SJ-03 | P0 | **진태양시 보정** 적용 |
| FR-SJ-04 | P0 | `birthTime` null → 시주 생략, 3주로 처리 |
| FR-SJ-05 | P0 | `birthRegion` null → 서울 기준(경도 127°) |
| FR-SJ-06 | P0 | 계산은 **결정적**. 같은 입력은 항상 같은 팔자 |
| FR-SJ-07 | P0 | 만세력을 **직접 구현하지 않는다.** 검증된 라이브러리 또는 절기 테이블 |
| FR-SJ-08 | P0 | `SajuCalculator` 는 Repository·Entity·스프링 컨텍스트를 참조하지 않는 순수 모듈 |
| FR-SJ-09 | P1 | 사용 라이브러리 라이선스가 MIT/Apache인지 확인하고 `docs/handoff.md` 에 기록 |

### 인수 조건

- **실제 인물 5명 이상**의 계산 결과가 공개 만세력 서비스와 일치
- 절기 경계 전후 하루(입춘 직전/직후)에 월주가 정확히 전환
- 자정 직전·직후에 일주가 정확히 전환
- `birthTime: null` → `hourUnknown: true`, `pillars.hour: null`
- `SajuCalculator` 단위 테스트가 **DB·스프링 컨텍스트 없이** 실행

> ⚠️ **프로젝트 최대 리스크.** Day 1에 라이브러리가 결정되지 않으면 전체 일정이 밀린다.

---

## 7. 해석 생성 (FR-RD) · 은호

| ID | P | 요구사항 |
|---|---|---|
| FR-RD-01 | P0 | 운명 제목·설명, 결혼운·자녀운·연애운, 행운 아이템·장소를 생성 |
| FR-RD-02 | P0 | **등급은 규칙 기반 코드가 산출.** LLM에 등급을 맡기지 않는다 |
| FR-RD-03 | P0 | LLM은 등급·팔자를 받아 **보살 톤 문장으로 각색만** 한다 |
| FR-RD-04 | P0 | 생성 결과를 `reading` 에 저장. `result_id` PK로 Result당 1행 |
| FR-RD-05 | P0 | 저장된 해석이 있으면 **LLM을 호출하지 않고** DB 값 반환 |
| FR-RD-06 | P0 | LLM 호출에 **타임아웃** 설정. 무한 대기 금지 |
| FR-RD-07 | P0 | LLM 실패 시 `LLM_UNAVAILABLE` 503. 팔자는 저장해 **재시도 가능**하게 |
| FR-RD-08 | P1 | 프롬프트는 리소스 파일로 분리. 코드에 문자열로 박지 않는다 |
| FR-RD-09 | P0 | 행운 아이템과 장소는 축제장에서 **바로 활용 가능한 결과**로 생성 |
| FR-RD-10 | P0 | 등급 산출 로직은 순수 함수. 같은 팔자는 항상 같은 등급 |

### Gemini 무료 티어 대응 (FR-GM)

| ID | P | 요구사항 |
|---|---|---|
| FR-GM-01 | P0 | **프롬프트에 생년월일·시간·지역·닉네임을 넣지 않는다.** 팔자와 등급만 |
| FR-GM-02 | P0 | **전체 운명 콘텐츠를 한 번의 호출로 생성**한다. 개별 호출 금지 |
| FR-GM-03 | P0 | 응답을 JSON으로 받아 파싱. 필드 누락·파싱 실패 시 `LLM_UNAVAILABLE` |
| FR-GM-04 | P0 | `429 RESOURCE_EXHAUSTED` → `LLM_UNAVAILABLE` 503으로 변환 |
| FR-GM-05 | P0 | 재시도는 **1회까지.** 무한 재시도는 한도를 더 빨리 태운다 |
| FR-GM-06 | P0 | 요청·응답 전문을 로그에 남기지 않는다. 토큰 수·소요 시간만 |
| FR-GM-07 | P1 | 실제 RPM·RPD 한도를 측정하고 `docs/handoff.md` 에 기록 |

> 무료 티어는 프롬프트·응답이 Google 제품 개선에 사용될 수 있다(사람 검토 포함).
> 팔자를 코드로 먼저 계산하는 구조 덕분에 개인정보를 보내지 않고도 해석을 만들 수 있다.
> 이 이점을 버리지 마라.

### 인수 조건

- 같은 `resultId` 를 10회 조회해도 LLM 호출 **0회**
- 같은 생년월일시로 두 번 생성 시 **등급 동일**
- 결과 1건 생성 시 LLM 호출이 **1회** (5회가 아니다)
- 전송된 프롬프트 문자열에 생년월일·닉네임이 포함되지 않는다 (테스트로 검증)
- LLM 강제 실패 시 500이 아니라 **503 + `LLM_UNAVAILABLE`**
- 실패 후 동일 요청 재시도 시 정상 생성

---

## 8. 결과 API (FR-RS) · 선우

| ID | P | 요구사항 |
|---|---|---|
| FR-RS-01 | P0 | `resultId` 는 **UUIDv4**. 순번·추측 가능한 값 금지 |
| FR-RS-01A | P0 | 공개 링크는 별도 UUIDv4 `shareId`를 사용하고 공유 응답에 내부 `resultId`를 노출하지 않는다 |
| FR-RS-02 | P0 | `POST /api/results` 는 계산→등급→해석→저장 후 201 |
| FR-RS-03 | P0 | `GET /api/results/{id}` 는 LLM 재호출 없이 DB에서 반환 |
| FR-RS-04 | P0 | 존재하지 않는 id → `RESULT_NOT_FOUND` 404 |
| FR-RS-05 | P0 | `fortunes` 는 항상 고정 순서 (MARRIAGE→CHILDREN→LOVE) |
| FR-RS-06 | P0 | 궁합 목록을 `createdAt` **내림차순**으로 포함 |
| FR-RS-07 | P0 | Controller는 Entity를 반환하지 않는다 |
| FR-RS-08 | P1 | 조회는 `@Transactional(readOnly = true)` |

### 인수 조건

- 발급된 `resultId` 가 UUID **v4** 형식이다 (버전 비트 확인)
- 연속 생성한 두 결과의 id가 인접하거나 예측 가능하지 않다
- 없는 UUID 조회 → 404
- `fortunes` 순서가 매 요청 동일

---

## 9. 친구 궁합 (FR-CP) · 선우

| ID | P | 요구사항 |
|---|---|---|
| FR-CP-01 | P0 | 두 `Result` 의 궁합 점수(0~100)와 Tier 산출 |
| FR-CP-02 | P0 | **`score(A,B) == score(B,A)`** 항상 성립 |
| FR-CP-03 | P0 | Tier: 90~100 `GUIIN` / 75~89 `CHALTTEOK` / 61~74 `BEOT` / 0~60 `SEUCHIM` (2026-09-13 기획 확정) |
| FR-CP-04 | P0 | 이미 존재하는 조합은 재계산하지 않고 기존 값을 **200**으로 반환 |
| FR-CP-05 | P0 | 링크 주인 결과와 `guestResultId`가 같으면 `SELF_COMPATIBILITY` 400 |
| FR-CP-06 | P0 | 존재하지 않는 `shareId` 또는 `guestResultId` → `RESULT_NOT_FOUND` 404 |
| FR-CP-07 | P0 | 응답·조회에 **상대의 생년월일·성별·resultId 미포함.** 닉네임과 점수만 |
| FR-CP-08 | P0 | `CompatibilityCalculator` 는 순수 함수 |
| FR-CP-09 | P1 | 동시 요청으로 같은 조합이 중복 생성되지 않는다 (UNIQUE + 예외 처리) |
| FR-CP-10 | P0 | 실제 팔자 표본의 Tier 목표 분포는 귀인 20% / 찰떡 30% / 벗 30% / 스침 20% (각 ±2%p) |

### 인수 조건

- A→B, B→A 점수 동일 (**자동 테스트로 강제**)
- 같은 조합 2회 요청 → 두 번째 200, 점수 동일, DB row 1개
- 궁합 응답 JSON에 `birthDate`, `birthTime`, `gender` 키가 없다
- 경계값 60/61/74/75/89/90에서 Tier가 정확히 나뉜다
- 고정 시드 실제 팔자 10만 조합에서 각 Tier가 목표 비율 ±2%p 안에 든다

---

## 10. 소개팅 사전등록 (FR-SU) · 도윤

| ID | P | 요구사항 |
|---|---|---|
| FR-SU-01 | P0 | 이메일·성별·선호성별·`resultId`(nullable)로 신청 생성 |
| FR-SU-02 | P0 | 학교 웹메일 **도메인 화이트리스트** 검증 → `INVALID_EMAIL_DOMAIN` 400 |
| FR-SU-03 | P0 | 이메일 중복 → `DUPLICATE_SIGNUP` 409 |
| FR-SU-04 | P0 | `resultId` nullable. 사주 없이 신청하는 경로 허용 |
| FR-SU-05 | P0 | 인증 메일 발송. 토큰은 `email_verification` 테이블, TTL 30분 |
| FR-SU-06 | P0 | 토큰은 `SecureRandom` 기반 URL-safe 문자열 |
| FR-SU-07 | P0 | 검증 조건 `expires_at > now() AND used_at IS NULL`. 성공 시 `used_at` 기록 |
| FR-SU-08 | P0 | 만료·위조·재사용 토큰 → `INVALID_TOKEN` 400 |
| FR-SU-09 | P0 | 인증 성공 시 프론트 완료 페이지로 **302**. 대상 URL은 설정값 |
| FR-SU-10 | P0 | 쿠폰은 **1인 1회** |
| FR-SU-11 | P0 | **이름·전화번호를 수집하지 않는다.** 스키마에 컬럼도 없다 |
| FR-SU-12 | P0 | **메일 발송 실패가 signup 생성을 롤백시키지 않는다.** 재발송 가능해야 한다 |
| FR-SU-13 | P0 | 재발송 API 제공. 이미 인증된 이메일이면 400 |
| FR-SU-14 | P1 | 신청자 수·성비 조회 쿼리를 문서화 |
| FR-SU-15 | P1 | SMTP 발송 계정의 일일 한도를 확인하고 기록 |

### 인수 조건

- `@gmail.com` 신청 → 400 `INVALID_EMAIL_DOMAIN`
- 같은 이메일 2회 신청 → 409, **쿠폰 2회 발급되지 않음**
- 만료 토큰·사용된 토큰 인증 → 400
- `resultId: null` 신청이 정상 생성
- **SMTP를 강제 실패시켜도 signup row는 남고 재발송이 가능하다**
- 존재하지 않는 `resultId` 신청 시 처리 방침이 정의되어 있다 (**Day 3까지 결정**)

---

## 11. 비기능 요구사항 (NFR)

### 성능

| ID | P | 담당 | 요구사항 |
|---|---|---|---|
| NFR-P-01 | P0 | 은호 | 결과 **생성**은 30초 내 응답하거나 타임아웃 처리 |
| NFR-P-02 | P0 | 선우 | 결과 **조회**는 1초 내 응답 (공유 링크 주 진입점) |
| NFR-P-03 | P0 | 공통 | `open-in-view: false` |
| NFR-P-04 | P0 | 공통 | DB·SMTP·LLM **모두 타임아웃 명시.** 기본값에 맡기지 않는다 |
| NFR-P-05 | P1 | 도윤 | 동시 100 요청에서 커넥션 풀 고갈 없음 |
| NFR-P-06 | P1 | 선우 | 조회 쿼리에 N+1 없음 |

### 데이터

| ID | P | 요구사항 |
|---|---|---|
| NFR-D-01 | P0 | 스키마 변경은 **Flyway가 먼저.** `ddl-auto: validate` 고정 |
| NFR-D-02 | P0 | 머지된 마이그레이션 파일은 **절대 수정하지 않는다.** 새 파일 추가 |
| NFR-D-03 | P0 | 마이그레이션 번호는 `docs/handoff.md` 예약 표로 선점 |
| NFR-D-04 | P0 | 모든 시각 컬럼은 `TIMESTAMPTZ`. `TIMESTAMP` 금지 |
| NFR-D-05 | P0 | enum은 DB에 `VARCHAR` + `@Enumerated(EnumType.STRING)` |
| NFR-D-06 | P0 | 연관관계는 `LAZY` 고정. `EAGER` 금지 |
| NFR-D-07 | P1 | 로컬 DB 초기화 후 재기동하면 스키마가 자동 재구성 |

### 보안·개인정보

| ID | P | 요구사항 |
|---|---|---|
| NFR-S-01 | P0 | `resultId` 는 UUIDv4. 순번이면 남의 결과를 전부 긁힌다 |
| NFR-S-02 | P0 | 시크릿은 환경변수. 코드·설정 파일 하드코딩 금지 |
| NFR-S-03 | P0 | 로그에 생년월일시·이메일 **평문 금지** |
| NFR-S-04 | P0 | **LLM 프롬프트에 개인정보 금지** (FR-GM-01) |
| NFR-S-05 | P0 | 에러 응답에 스택트레이스·SQL 오류 노출 금지 |
| NFR-S-06 | P0 | CORS 화이트리스트. 와일드카드 금지 |
| NFR-S-07 | P0 | 축제 종료 +2주 전량 파기. **파기 스크립트를 미리 만든다** |
| NFR-S-08 | P0 | 사주 단계 데이터를 이메일과 묶지 않는다 |
| NFR-S-09 | P0 | `application-local.yml` 은 `.gitignore`. 저장소엔 `.example` 만 |

### 운영

| ID | P | 담당 | 요구사항 |
|---|---|---|---|
| NFR-O-01 | P0 | 도윤 | **Day 1에 배포 파이프라인 동작** (`/api/health` 200) |
| NFR-O-02 | P0 | 도윤 | `dev`→`main` 머지 시 프로덕션 자동 배포. `dev` PR은 CI만 |
| NFR-O-03 | P0 | 도윤 | **롤백을 Day 6에 실제로 한 번 해본다** |
| NFR-O-04 | P1 | 도윤 | 에러 로그 실시간 확인 가능 |
| NFR-O-05 | P1 | 도윤 | 축제 기간 인스턴스 스펙 업 절차 정리 |
| NFR-O-06 | P1 | 은호 | LLM 호출 횟수를 집계할 수 있다 (무료 한도 관리) |

### 협업 (3명 · 서로 다른 AI 도구)

| ID | P | 요구사항 |
|---|---|---|
| NFR-T-01 | P0 | 규칙 원본은 루트 `AGENTS.md` 하나. 도구별 파일은 포인터. **복붙 금지** |
| NFR-T-02 | P0 | 작업 전 `docs/handoff.md` 를 읽고, 끝나면 기록한다 |
| NFR-T-03 | P0 | `.editorconfig` 를 둔다. 나중에 포매터를 넣으면 전 파일이 diff에 걸린다 |
| NFR-T-04 | P0 | AI 생성 코드도 PR 리뷰를 거친다 |
| NFR-T-05 | P0 | PR 체크리스트의 "AI 생성 코드 검증" 항목을 매번 확인 |
| NFR-T-06 | P0 | 남의 담당 패키지를 수정하지 않는다 |
| NFR-T-07 | P0 | CI에서 빌드·테스트 실패 시 머지가 차단된다 |
| NFR-T-08 | P0 | 작업은 **이슈 → 브랜치 → PR(base: dev)** 순서. 이슈 없는 브랜치 금지 |
| NFR-T-09 | P1 | 이슈에 관련 요구사항 ID를 적는다 |

---

## 12. 테스트 요구사항 (TR)

| ID | P | 담당 | 대상 | 이유 |
|---|---|---|---|---|
| TR-01 | P0 | 은호 | 팔자 계산 — 실제 인물 5명, 만세력 대조 | 틀리면 서비스가 무의미 |
| TR-02 | P0 | 은호 | 시간·지역 **null** 케이스 | NULL 처리에서 가장 자주 터진다 |
| TR-03 | P0 | 은호 | **프롬프트에 개인정보 미포함** 검증 | 무료 티어 데이터 사용 리스크 |
| TR-04 | P0 | 선우 | 궁합 대칭성 `score(A,B)==score(B,A)` | 깨지면 유저가 즉시 알아챈다 |
| TR-05 | P0 | 선우 | 해석 캐싱 — 재조회 시 LLM 호출 0회 | 비용·한도 직결 |
| TR-06 | P1 | 선우 | Tier 경계값 60/61/74/75/89/90 | off-by-one 빈발 |
| TR-07 | P1 | 도윤 | SMTP 실패 시 signup 유지 | 성비 데이터 손실 방지 |

### 환경 규칙

| ID | P | 요구사항 |
|---|---|---|
| TR-E-01 | P0 | LLM 호출은 **반드시 목킹.** 테스트가 무료 한도를 태우면 안 된다 |
| TR-E-02 | P0 | DB 테스트는 **Testcontainers(PostgreSQL)** |
| TR-E-03 | P0 | H2 금지. 문법 차이로 **로컬 통과 → 배포 실패**가 난다 |
| TR-E-04 | P0 | 통합 테스트는 공통 베이스 클래스 상속. 각자 설정을 짜면 CI가 느려진다 |
| TR-E-05 | P0 | CI에서 테스트 자동 실행, 실패 시 머지 차단 |

---

## 13. 릴리즈 판정 체크리스트 (Day 6~7)

### 기능

- [ ] 시간·지역 **모름**으로 결과가 정상 생성
- [ ] 실제 인물 5명의 팔자가 공개 만세력과 일치
- [ ] 같은 입력 2회 생성 → 등급 동일
- [ ] 결과 1건 생성 시 **LLM 호출 1회** (5회가 아니다)
- [ ] 결과 재조회 시 LLM 호출 0회
- [ ] **프롬프트에 생년월일·닉네임이 없다**
- [ ] `fortunes` 순서가 항상 고정
- [ ] `score(A,B) == score(B,A)`
- [ ] 같은 궁합 조합 2회 요청 → 200, DB row 1개
- [ ] 궁합 응답에 상대 생년월일·성별 없음
- [ ] Tier 경계값이 정확히 나뉨
- [ ] 자기 자신 궁합 → 400 / 없는 resultId 조회 → 404
- [ ] 외부 도메인 이메일 → 400 / 이메일 중복 → 409, 쿠폰 1회
- [ ] 인증 메일 수신 및 링크 동작
- [ ] 만료·재사용 토큰 → 400
- [ ] **SMTP 강제 실패 시 signup 유지 + 재발송 동작**

### 비기능

- [ ] 결과 조회 1초 내 응답
- [ ] 동시 100 요청 부하 테스트 통과
- [ ] **Gemini 무료 티어 RPM·RPD 실측값을 안다**
- [ ] `open-in-view: false`, `ddl-auto: validate` 확인
- [ ] 없는 URL 호출 시 HTML이 아니라 JSON
- [ ] 로그에 생년월일시·이메일 평문 없음
- [ ] 로그·에러 응답의 traceId 일치
- [ ] 응답에 스택트레이스 없음
- [ ] 레포에 시크릿 커밋 없음 / CORS 와일드카드 아님
- [ ] `resultId` 가 UUIDv4
- [ ] Swagger 파라미터명이 실제 이름으로 표시

### 운영

- [ ] `/api/health` 200 (배포 도메인)
- [ ] `dev`→`main` 머지 → 프로덕션 자동 배포 동작
- [ ] `main`·`dev` 브랜치 보호 설정 완료
- [ ] **롤백을 실제로 한 번 해봤다**
- [ ] 데이터 파기 스크립트 작성 완료
- [ ] CI에서 테스트 자동 실행

---

## 14. 개발 순서 제약

| 순서 | 항목 | 담당 | 이유 |
|---|---|---|---|
| 1 | `docs/api-spec.md` 확정 → 프론트 전달 | 공통 | 없으면 **프론트 3명 착수 불가** |
| 2 | CORS에 프론트 로컬 origin 허용 | 도윤 | 없으면 프론트가 API를 못 부른다 |
| 3 | 배포 파이프라인 (`/api/health` 200) | 도윤 | 미루면 마지막 날이 배포 삽질로 사라진다 |
| 4 | 만세력 라이브러리 결정 | 은호 | 미정이면 사주 파트 전체가 대기 |
| 5 | `V1__init.sql` · `docker-compose.yml` 커밋 | 도윤 | 3명이 동시에 만들면 반드시 충돌 |
| 5 | `main`·`dev` 생성 + 브랜치 보호 + default를 `dev` 로 | 도윤 | 없으면 PR base 실수가 난다 |
| 6 | 더미 해석으로라도 `POST /api/results` 동작 | 선우 | 프론트가 목 → 실API 전환 가능 |

**1~5는 Day 1. 6은 Day 2.**

> `V1__init.sql` 과 `docker-compose.yml` 은 **한 사람이 먼저 만들어 커밋**한다.
> 3명이 각자 AI로 만들면 스키마가 세 가지로 갈라진다.

---

## 15. 가정 및 미결정

| 가정 | 확인 필요 |
|---|---|
| 선릴리즈는 축제 D-day 이전이다 | D-day 미확정 |
| MIT/Apache 만세력 라이브러리가 존재한다 | **Day 1 확인** |
| Gemini 무료 티어 한도로 축제 트래픽을 감당할 수 있다 | **Day 6 실측** |
| 학교 웹메일 도메인을 확정할 수 있다 | 미확정 |
| SMTP 발송 계정을 확보할 수 있다 | 일일 한도 확인 필요 |
| 단일 인스턴스로 운영한다 | 다중이면 캐시·배치 전략 재검토 |
| 개발 서버 없이 `main` 하나로 운영한다 | **Day 1 결정** — 없으면 프론트가 프로덕션에 붙는다 |

### 미결정 사항

| 항목 | 시점 | 담당 |
|---|---|---|
| 축제 D-day / 선릴리즈 일자 | 즉시 | 도윤 |
| 만세력 라이브러리 | **Day 1** | 은호 |
| 프론트 배포 도메인 (CORS) | Day 1 | 도윤 |
| 개발 서버 별도 운영 여부 | Day 1 | 도윤 |
| Gemini 모델명·타임아웃 값 | Day 2 | 은호 |
| 프롬프트 톤 확정 | Day 2 밤 | 은호 + 기획 |
| 학교 웹메일 화이트리스트 | Day 3 | 도윤 |
| SMTP 발송 계정 + 일일 한도 | Day 3 | 도윤 |
| 없는 `resultId` 신청 시 처리 방침 | Day 3 | 도윤 |
| 데이터 파기 스크립트 | Day 5 | 도윤 |
| Gemini 무료 티어 RPM·RPD 실측 | Day 6 | 은호 |
| 무료 한도 부족 시 결제 계정 연결 여부 | Day 6 | 도윤 |

**결정되면 표에서 지우고 본문에 반영한다.**

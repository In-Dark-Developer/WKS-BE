# handoff.md

**작업 시작 전에 읽고, 끝나면 쓴다.** 예외 없음.

3명이 **각자 다른 AI 도구**로 병렬 작업한다.
도구별 컨텍스트는 공유되지 않고, 슬랙은 흘러가고, 커밋 메시지는 "왜 그렇게 했는지"를 담지 못한다.
**이 파일이 팀의 유일한 공유 기억이다.** 도구가 달라도 사람이 남긴 기록은 모든 AI가 읽는다.

작성 규칙:
- 최신 항목이 **위**로
- 템플릿 그대로 채운다. 형식을 바꾸지 않는다
- 3일 지난 항목은 `## 지난 기록` 으로 내린다

---

## 현재 상태 (매일 갱신)

| 항목 | 상태 |
|---|---|
| 릴리즈 D-day | 축제 2026-09-29 ~ 10-01 (2026-09-21 확정) |
| `main`·`dev` 브랜치 생성 + 보호 설정 | ✅ 생성. 보호 설정은 private 저장소 무료 플랜이라 불가 (PR 리뷰로 대체) |
| 배포 상태 | 🔧 파일상 확정(2026-09-23): `main` push → `deploy.yml` → 운영, `dev` push → `deploy-dev.yml` → 개발 서버. **아직 커밋 전이고 EC2에도 미반영** — 커밋·머지 전까지는 실제로 `dev` push 가 운영을 배포하는 기존 동작 그대로다. 머지 직후엔 `main` 으로 한 번도 릴리즈 안 해봐서 첫 `dev`→`main` PR 전까지 운영 자동 배포 공백 생김(아래 기록) |
| 개발 서버 (`api-dev.threadoffate.site`) | 🔧 인프라 파일 준비 완료(2026-09-23, 아래 기록) — **EC2 미적용.** `docs/runbook-dev-server.md` 대로 본인이 실행해야 실제로 뜬다 |
| `/api/health` (배포 도메인) | ✅ 200 (2026-09-15 확인) |
| Flyway 최신 버전 | V11 (dev 기준, 2026-09-23 로그인 머지). V12(원장)는 예약만, V13(궁합 이유 캐시, #81)·V14(잘 맞는 오행, #82)는 PR — 아래 예약 표. **#81 → #82 순서로 머지** |
| 카카오 로그인 | ✅ 백엔드 `dev` 머지 완료(V11). 🔧 **2026-09-25, 토큰 전달을 Bearer 헤더→HttpOnly 쿠키로 전환**(백엔드 `feat/cookie-based-auth`, PR 대기) — **프론트(WKS-FE) 대응 전까지는 로그인이 깨진다.** 아래 2026-09-25 기록의 "프론트에 알려야 할 것" 참고 |
| api-spec 프론트 전달 | ❌ 미전달 |
| CORS localhost:3000 허용 | ✅ 기본값 (`CORS_ALLOWED_ORIGINS` 로 덮어씀). 프론트 배포 도메인은 미반영 |

### 지금 막혀 있는 것

| 내용 | 담당 | 필요한 것 |
|---|---|---|
| 프론트 배포 도메인 (CORS용) | 곽도윤 | 프론트 팀 확인 |
| 개발 서버 별도 운영 여부 | 곽도윤 | ✅ 2026-09-23 결정(둔다) + diff 승인 완료. 남은 건 커밋·PR과 `docs/runbook-dev-server.md` 실제 EC2 실행 — 아래 2026-09-23 인프라 기록 2건 |
| SMTP 발송 계정 | 곽도윤 | 발송 한도 확인 필요 |
| **`.env.prod.example` 에 실제 비밀값이 들어가 있다** (DB 비번·Gemini 키·카카오 client-id/secret) | 곽도윤 | **2026-09-23, 본인 확인 후 "private 저장소라 상관없다"며 정리 보류 결정.** AGENTS.md·#72 재발 사고와 같은 패턴이라 다음 사람은 참고할 것 — 저장소 public 전환 얘기 나오면 반드시 재검토 |
| 카카오 디벨로퍼스 앱 설정 | 곽도윤 | ✅ 완료(2026-09-23). REST API 키·Client Secret 발급, Redirect URI `localhost:3000/dev/kakao-callback` 등록(주의: 5173 아님, 아래 기록 참고), 카카오 로그인 활성화 |
| JWT 라이브러리 도입 승인 | 곽도윤 | `nimbus-jose-jwt` 로컬에 이미 추가돼 동작 확인함(2026-09-23). **팀 채널 공지는 아직 안 함** — convention 규칙상 커밋 전에 알릴 것 |
| plan.md 미결정 항목 (TBD-13~16 등) | 기획 | 소개팅 BE 1차(09/25)에 영향. 명세가 09/22 전에 확정돼야 함 |
| **CI 없음.** PR 용 빌드·테스트 워크플로가 없고 `deploy.yml` 은 `bootJar -x test` | 곽도윤 | `dev` push 가 테스트 없이 운영에 배포된다. PR CI(`./gradlew build`) 추가와 배포 전 테스트 단계 결정 |
| PR #81(궁합 상세 이유)·#83(잘 맞는 오행) 리뷰·머지 | 최선우·곽도윤 | **#81 → #83 순서.** #83 은 #81 위에 쌓은 PR(base `feat/79-compatibility-reason`)이라 #81 머지 후 `gh pr edit 83 --base dev`. Flyway V13 → V14. `ErrorCode`·`db/migration/`·`compatibility/`·`result/` 변경 **팀 채널 공지는 아직 안 함**(차은호) |
| "나와 잘 맞는 오행" 선정 규칙 | 기획 | #83 은 보완 오행(`LuckyPlace.luckyElement`, 행운의 장소와 동일)으로 잡았다. 기획 규칙이 다르면 함수 하나만 교체 |
| 사주 해설 문장 길이 | 기획 | 피그마(`8:741`) 연애·결혼·자녀운 본문은 6문장 안팎, 현재 프롬프트는 8~10문장. 줄일지 결정 |

---

## Flyway 번호 예약

**파일 만들기 전에 여기 먼저 적는다.** 번호 충돌은 가장 자주 나는 사고다.

| 번호 | 예약자 | 내용 | 상태 |
|---|---|---|---|
| V17 | 최선우 | 소개팅 요청·수락/거절 상태 및 양방향 중복 방지 (#86) | 작업 중, V16 뒤에 머지 |
| V16 | 최선우 | `dating_profile.result_id` 직접 FK 제거. V15 뒤에 머지 | #84 로컬 기동 검증·미머지 |
| V15 | 최선우 | 소개팅 사진·프로필·추천 노출 이력. V12~V14 뒤에 머지 | #84 `feat/84-dating-profile-recommendations`, 검증 완료·미머지 |
| V14 | 차은호 | `reading` 에 `element_match_content` (잘 맞는 오행 이유, #82). **V13(#81) 뒤에 머지** | PR |
| V13 | 차은호 | `compatibility` 에 `reason_why`·`reason_together`·`reason_conflict` (궁합 상세 이유 캐시, #80) | PR |
| V12 | 미정 | `thread_ledger` (실 원장, `ref_id NOT NULL`). 소개팅 BE 와 함께 | 예약 |
| V11 | 곽도윤 | `member`(`kakao_id` 만) + `result.member_id`(계정당 1개, 부분 unique). 로그인 마감 09/22 | 로컬 적용·검증 완료(2026-09-23), **커밋 전** |
| V10 | 곽도윤 | signup 에 `photo_key` 추가 (#54). `V9__add_signup_photo_key.sql` 을 개명 (dev 의 V9 와 중복이었다) | 개명 완료 (2026-09-21), PR 대기 |
| V9 | 차은호 | result 에 `calendar_type`·`birth_date_input`·`is_leap_month` 추가. 입력 폼 자동 채움 | PR |
| V8 | 차은호 | reading 에 `version` 컬럼 + result (birth_date, birth_time, gender) 인덱스. 같은 입력 해석 재사용 | PR |
| V7 | 곽도윤 | signup에 `name`·`contact_method`·`contact_value`·`department`·`mbti`·`bio` 컬럼 추가 | 구현 완료 |
| V6 | 최선우 | result `share_id` + 궁합 A↔B 무순서 유니크 인덱스 + guest 조회 인덱스 | 구현 완료 |
| V5 | 차은호 | reading 의 `destiny_title` 삭제 (조회 시 계산) | 완료 |
| V4 | 차은호 | reading 의 `lucky_item`·`lucky_place` 삭제 (조회 시 계산) | 완료 |
| V3 | 차은호 | reading 의 등급 컬럼을 0~100 점수 컬럼으로 교체 (`*_grade` → `*_score`) | 완료 |
| V2 | 최선우 | 운명·등급·행운 콘텐츠 저장을 위한 reading 확장 | 완료 |
| V1 | 곽도윤 | init schema (5개 테이블) | 완료 |

> V13 이상은 소개팅·궁합 이유 캐시 등이 예약한다. 사주 리팩토링(차은호)도 번호를 이 표에 먼저 적는다.

> **머지 순서 = 번호 순서.** Flyway 의 `outOfOrder` 가 꺼져 있어(기본값) V11 이 운영에 적용된 뒤 V10 이 들어오면 앱이 기동하지 못한다. `dev` push 가 곧 운영 배포이므로 **#54(V10)를 로그인(V11)보다 먼저 머지**한다. 순서가 바뀌면 나중 PR 의 번호를 바꾼다.

---

## ErrorCode 추가 현황

`common/exception/ErrorCode.java` 는 3명이 다 건드린다. 추가 전 여기 적는다.

| code | 추가자 | api-spec 반영 |
|---|---|---|
| (문서 기준 8종) | - | ✅ |
| `UNAUTHENTICATED` (401) | 곽도윤 (예정, 로그인 PR) | 📄 `api-spec.md` §9 "추가 예정 에러 코드"에 기재. **§1 표에는 구현 PR 에서 `ErrorCode` 와 함께** 옮긴다 (1:1 유지) |
| `KAKAO_UNAVAILABLE` (503) | 곽도윤 (확정 2026-09-21, 로그인 PR) | 📄 `api-spec.md` §9 에 기재. 카카오 서버 오류·타임아웃. **§1 표에는 구현 PR 에서 `ErrorCode` 와 함께** 옮긴다 |
| `COMPATIBILITY_NOT_FOUND` (404) | 차은호 (#80) | ✅ §1 표·§4 `GET /api/compatibilities/{id}/reason` |
| `INSUFFICIENT_THREAD` | 소개팅 BE (예정) | ❌ HTTP 상태 미정 (plan.md TBD-11). 명세 확정 후 |
| `DATING_PROFILE_NOT_FOUND` (404) | 최선우 (소개팅 프로필) | ✅ §1·§10 |
| `DATING_PROFILE_CONFLICT` (409) | 최선우 (중복 프로필·이메일) | ✅ §1·§10 |
| `DATING_NOT_VERIFIED` (403) | 최선우 (학교 메일 미인증) | ✅ §1·§10 |
| `DATING_REQUEST_NOT_FOUND` (404) | 최선우 (#86) | ✅ §1·§11 |
| `DATING_REQUEST_CONFLICT` (409) | 최선우 (#86) | ✅ §1·§11 |

---

## 프론트에 공지한 API 변경

| 날짜 | 변경 내용 | 공지함 |
|---|---|---|
| 2026-09-25 | **[필드 삭제, 프론트 대응 필수]** `POST /api/auth/kakao` 응답에서 `token` 필드 제거. 토큰은 이제 `Set-Cookie`(HttpOnly)로만 내려간다 — 인증 필요 API는 `credentials: 'include'` 로 호출. `POST /api/auth/logout` 신규 추가(로그아웃은 이제 이 호출로 처리, 클라이언트 로컬 삭제 방식 폐기). 상세는 `docs/api-spec.md` §9, 백엔드 `feat/cookie-based-auth` | ❌ |
| 2026-09-23 | `POST /api/results`·`GET /api/results/{resultId}` 응답에 `elementMatch: { element, korean, reason }` 추가 (나와 잘 맞는 오행 + 이유, 기능명세 3.5). `null` 이면 영역 미노출. CTA "OO 기운의 사람 만나보기"는 `element` 사용 | ❌ |
| 2026-09-23 | `GET /api/compatibilities/{id}/reason` 추가 (궁합 상세 이유 3답: `why`·`together`·`conflict`). 첫 호출만 LLM 생성이라 최대 30초, 실패는 `LLM_UNAVAILABLE` 503 → 해당 영역만 미노출·재시도. 두 사람이 같은 내용. **프론트가 `id` 를 받으려면 궁합 응답에 `id` 가 필요** — 아래 기록 참고 | ❌ |
| 2026-09-21 | **(예정, 미구현)** `POST /api/auth/kakao`(프론트가 code 전달, 응답에 JWT)·`GET /api/me`·`GET /api/me/result`. **초안이 `api-spec.md` §9 / `api.md` §6 에 있음.** 사주·궁합·공유 API 는 **변경 없음**(비로그인 그대로). 인증 API 는 `Authorization: Bearer`. 프론트 콜백 주소(운영·로컬)를 백엔드에 받아야 한다. 구현 PR 에서 확정 후 재공지 | ❌ |
| 2026-09-17 | `PATCH /api/results/{resultId}` 추가 (닉네임만 변경, 공유 링크·궁합 유지) | ❌ |
| 2026-09-17 | `GET /api/results/{resultId}/input` 추가 (폼 자동 채움). `resultId` 는 URL 노출 금지 | ❌ |
| 2026-09-13 | 본인용 `resultId`와 공개용 `shareId` 분리. `GET /api/shares/{shareId}`, 궁합 POST 추가 | ❌ |
| 2026-09-13 | `luckyItem`·`luckyPlace` 가 오늘 기준으로 매일 바뀜 (조회마다 재계산) | ❌ |
| 2026-09-13 | 궁합 `tier` 구간 변경: 90/75/61 경계 (25점 구간 아님) | ❌ |
| 2026-09-13 | 결과 응답에 `zodiac`(십이간지 enum) 추가. `grade` 6단계 `SS S A+ A B+ B` 확정 | ❌ |
| 2026-09-13 | `POST /api/results` 요청에서 `birthRegion` 제거 (기획 결정, 시진 입력이라 무의미) | ❌ |
| 2026-09-13 | `POST /api/results` 요청에 `calendarType`(필수)·`isLeapMonth` 추가 (음력 지원). 시진 가운데 시각 전송·자시 두 칸 분리 규칙 명시 | ❌ |
| 2026-09-12 | 결과 응답을 운명 제목·설명, 결혼/자녀/연애 등급·설명, 행운 아이템·장소로 변경 | ❌ |
| 2026-09-12 | `POST /api/results` 닉네임 최대 길이 20자 → 8자 | ❌ |
| - | - | - |

---

## 기록 템플릿

새 항목은 아래를 복사해 이 줄 바로 밑에 붙인다.

```markdown
### YYYY-MM-DD (요일) · 이름 · 담당 패키지 · 사용 도구

**한 일**
-

**건드린 파일/패키지**
-

**다음 사람이 알아야 할 것**
- (스펙 변경, 새 규칙, 함정, 임시 처리한 부분)

**막힌 것 / 넘기는 것**
- (없으면 "없음")

**문서 변경**
- (AGENTS.md / api-spec / architecture / conventions 중 고친 게 있으면)

**프론트에 알려야 할 것**
- (API 변경이 있으면. 없으면 "없음")
```

---

## 기록

### 2026-09-25 (금) · 곽도윤 · auth/·common/auth/ 로그인을 쿠키 기반으로 전환 · Claude Code

**한 일**
- **의도적 결정 (본인 확인).** 로그인 토큰 전달 방식을 `Authorization: Bearer` 헤더 + 프론트 로컬 저장소에서
  **HttpOnly 쿠키**로 바꿨다. `AGENTS.md`의 기존 "쿠키를 넣지 않는다" 규칙을 뒤집는 거라 진행 전에 재확인
  받았다 — Spring Security·서버 쪽 세션 저장소는 여전히 안 쓴다(쿠키는 JWT 를 담는 그릇일 뿐)
- `common/auth/JwtCookie`(신규) — 쿠키 발급(`issue`)·삭제(`clear`)·요청에서 읽기(`readFrom`)를 한 곳에
  모았다. 속성: `HttpOnly`·`SameSite=Lax`·`Path=/`·`Domain` 없음(host-only — 운영/개발 쿠키가 안 섞임)·
  `Secure`는 `app.auth.cookie-secure`(기본 true, 로컬만 false)로 제어
- `JwtAuthInterceptor`가 이제 쿠키에서만 토큰을 읽는다 (`Authorization` 헤더 파싱 제거 — 폴백 없이 완전
  전환). `/api/me/**`·`/api/dating/**` 둘 다 같은 인터셉터라 자동으로 적용됨
- `POST /api/auth/kakao` 응답에서 `token` 필드를 **삭제**했다 (프론트 계약 변경, api-spec.md·api.md 갱신).
  대신 응답에 `Set-Cookie`가 실린다. `AuthService.login()`은 이제 `LoginOutcome(token, body)`를 돌려주고
  Controller가 `token`을 꺼내 쿠키를 만든 뒤 `body`만 클라이언트에 보낸다
- `POST /api/auth/logout` 신규 추가 — 쿠키를 지운다. **쿠키가 HttpOnly라 프론트 JS가 못 지우므로 이
  엔드포인트가 없으면 로그아웃 자체가 불가능해진다** (2026-09-23 결정 "로그아웃은 클라이언트가 로컬
  토큰 삭제"는 이제 성립 안 함 — 그 결정을 대체함)
- `WebConfig`에 `allowCredentials(true)` 추가 (쿠키가 크로스 오리진 요청에 실리려면 필수 — CORS
  `allowedOrigins` 와일드카드 금지 규칙과도 호환됨, credentials 모드에서는 와일드카드 자체가 금지라)
- `OpenApiConfig`의 Swagger 보안 스키마를 `bearerAuth`(HTTP Bearer)에서 `cookieAuth`(APIKEY, in=COOKIE,
  name=wks_token)로 바꿨다. `MeController`·`DatingController`·`DatingRequestController`의
  `@SecurityRequirement` 이름도 맞춰 바꿈
- CSRF는 별도 토큰 없이 `SameSite=Lax` + 상태변경 API가 전부 POST/PATCH인 것으로 막는다 — CSRF 토큰
  시스템(Spring Security 등)은 도입하지 않았다(범위 밖이라고 판단, 필요하면 별건)
- **기존 테스트 중 하나가 깨졌다**: `DatingSchemaTest.datingRouteRequiresBearerToken`이 `Authorization`
  헤더로 인증하던 걸 전제해서 401 대신 예상한 404가 안 나왔다 — 쿠키를 심도록 고치고 테스트명도
  `datingRouteRequiresLoginCookie`로 바꿈. `AuthProperties`에 `cookieSecure` 필드가 추가돼 생성자를
  직접 호출하던 3개 테스트(`KakaoClientTest`·`RedirectUriPolicyTest`·`JwtProviderTest`)도 인자 추가
- 신규 테스트: `JwtCookieTest`(쿠키 속성 단위테스트), `AuthControllerTest`(로그인 응답에 토큰 필드가
  없고 Set-Cookie가 실리는지, 로그아웃이 Max-Age=0 쿠키를 내려주는지)
- `./gradlew build` 전체 통과 확인 (컴파일 + 전체 테스트 스위트)
- 문서 갱신: `AGENTS.md`(핵심 규칙 문구 교체), `docs/api-spec.md` §9(요청/응답 예시·인증 규칙·로그아웃
  엔드포인트 추가), `docs/architecture.md`(다이어그램·설계원칙 4·8·9, API 표), `docs/convention.md`(인증
  절), `docs/plan.md` §8(한 줄), `api.md` §6(프론트 공유용, 같은 내용)

**건드린 파일/패키지**
- 신규: `common/auth/JwtCookie.java`, `common/auth/JwtCookieTest.java`, `auth/AuthControllerTest.java`
- 수정: `common/auth/AuthProperties.java`(`cookieSecure` 필드), `common/auth/JwtAuthInterceptor.java`,
  `common/config/WebConfig.java`, `common/config/OpenApiConfig.java`, `auth/AuthController.java`,
  `auth/AuthService.java`, `auth/dto/KakaoLoginResponse.java`, `member/MeController.java`,
  `dating/DatingAuthWebConfig.java`(주석)·`DatingController.java`·`DatingRequestController.java`(Tag·
  SecurityRequirement), `application.yml`·`application-local.yml.example`(`cookie-secure`)
- 테스트 수정: `KakaoClientTest.java`, `RedirectUriPolicyTest.java`, `JwtProviderTest.java`(생성자 인자),
  `dating/DatingSchemaTest.java`(쿠키로 전환)
- 문서: 위 "한 일" 참고

**다음 사람이 알아야 할 것**
- **프론트(WKS-FE)가 반드시 같이 바뀌어야 한다.** 이 레포는 백엔드뿐이라 프론트는 못 건드렸다 — 아래
  "프론트에 알려야 할 것" 그대로 전달 필요. 프론트가 안 바뀐 채로 이 백엔드가 배포되면 **로그인이 완전히
  깨진다** (프론트가 여전히 응답 바디의 `token`을 찾고 `Authorization` 헤더를 보내려 하기 때문)
- 로컬 개발 시 `application-local.yml`에 `app.auth.cookie-secure: false`가 없으면 브라우저가 로그인
  쿠키를 저장하지 않는다 (plain HTTP라 Secure 쿠키 거부됨) — `application-local.yml.example`에 이미
  반영해뒀으니 로컬 파일도 맞춰 갱신할 것
- `wks_token` 쿠키는 host-only(Domain 속성 없음)라 `api.threadoffate.site`와 `api-dev.threadoffate.site`
  가 쿠키를 안 공유한다 — 의도한 설계(운영/개발 격리)
- CSRF 방어가 `SameSite=Lax`뿐이라는 걸 인지할 것. 상태변경 API를 GET으로 만들면 이 방어가 뚫린다 —
  새 API 만들 때 메서드를 지킬 것
- 이 브랜치(`feat/cookie-based-auth`, `dev`에서 분기)는 아직 커밋만 했고 PR은 안 올렸다

**막힌 것 / 넘기는 것**
- 곽도윤: 이 PR 리뷰·머지, 프론트 팀에 아래 변경사항 전달
- 프론트 팀 (WKS-FE, 별도 레포라 여기서 직접 수정 못 함):
  1. 로그인 API 응답에서 `token` 필드 제거됨 — 더 이상 읽지 않는다
  2. `localStorage`(`wks:auth`)에 토큰 저장하던 로직(`src/api/authToken.ts` 등) 전체 제거
  3. 인증이 필요한 모든 API 호출에 `credentials: 'include'`(axios는 `withCredentials: true`) 추가 —
     빠지면 브라우저가 쿠키를 안 보내서 401
  4. `LogoutButton.tsx`가 `clearAuthToken()`(로컬 삭제) 대신 `POST /api/auth/logout`을 호출하도록 변경
     — HttpOnly 쿠키는 JS가 못 지운다
  5. 카카오 콜백 처리 후 더 이상 토큰을 저장할 필요 없음 — 로그인 성공 응답만 받으면 쿠키는 이미 심어져
     있다
  6. 로컬 개발 시 프론트가 `localhost:8080`(백엔드)과 다른 포트(`5173`/`3000`)에서 뜨는데, 쿠키가
     `SameSite=Lax`라 같은 사이트(`localhost`)면 포트가 달라도 전송된다 — 별도 처리 불필요하지만
     `credentials: 'include'`는 로컬에서도 꼭 필요

**문서 변경**
- `AGENTS.md`, `docs/api-spec.md`, `docs/architecture.md`, `docs/convention.md`, `docs/plan.md`,
  `api.md`, `docs/handoff.md`(이 항목)

**프론트에 알려야 할 것**
- 위 "막힌 것 / 넘기는 것" 항목 1~6 그대로. **이번 백엔드 변경은 프론트 협업 없이는 배포하면 안 된다**
  (로그인이 깨짐) — 프론트 작업이 끝나고 같이 배포할 것

### 2026-09-24 (목) · 최선우 · dating/ 매칭 요청 API (#86) · Codex

**한 일**
- #84 머지를 확인한 최신 `origin/dev`에서 `feat/86-dating-requests` 브랜치를 만들었다
- 현재 추천 카드 상대에게 무료 요청, 보낸·받은 요청 목록, 수락·거절 및 수락 후 양쪽 연락처 공개를 구현했다. 실 원장은 사용하지 않는다
- 회원 행을 순서대로 잠가 동시 수락을 직렬화하고, 두 프로필 쌍의 요청은 DB UNIQUE 인덱스로 중복을 차단했다
- 정책 변경에 따라 수락 후에도 양쪽 프로필은 소개팅을 계속 이용하고 추천 후보 자격을 유지한다. `matched_at`은 더 이상 쓰지 않는다
- PostgreSQL Testcontainers 포함 전체 `./gradlew test --offline` 통과

**건드린 파일/패키지**
- `dating/` 요청 Controller·Service·Repository·Entity·DTO, 추천 Repository, `db/migration/V17__add_dating_request.sql`, `common/exception/ErrorCode.java`, `DatingSchemaTest`

**다음 사람이 알아야 할 것**
- `requestId`는 요청 UUID, `candidateId`는 조회자 기준 상대 프로필 UUID다. 연락처는 `ACCEPTED`일 때만 응답한다
- 요청은 무료로 확정돼 `plan.md` TBD-5를 종료했다. 새 요청은 현재 추천 카드의 후보에게만 가능하고 한 쌍당 한 번만 가능하다
- 매칭 수락 후에도 다른 사람에게 요청하거나 요청받을 수 있다. 연락처 공개는 수락된 요청의 두 사람 사이에서만 이뤄진다

**막힌 것 / 넘기는 것**
- #84에서 남은 학교 이메일 인증 연동이 되기 전에는 추천 조회가 403이므로 실제 요청 왕복도 진행할 수 없다
- `common/exception/ErrorCode.java`와 `db/migration/` 변경의 팀 채널 사전 공지는 사용자가 이전에 하지 않겠다고 했다

**문서 변경**
- `docs/plan.md` 무료 정책 확정, `docs/api-spec.md` §1·§11 프론트 계약, `docs/handoff.md` V17 예약·에러 코드·기록

**프론트에 알려야 할 것**
- 매칭 요청은 무료. §11의 요청 생성·보관함·수락/거절 API 및 수락 전후 연락처 공개 조건 전달 필요

---

### 2026-09-24 (목) · 최선우 · dating/ 프로필·Top 3 API (#84) · Codex

**한 일**
- 분리된 `dev` 작업 공간에서 소개팅 사진 업로드 URL·프로필 등록/조회/수정·현재 Top 3 조회를 구현했다. 추천은 기존 `CompatibilityCalculator`만 사용하며 친구 궁합 행이나 LLM을 생성하지 않는다
- 프로필은 학교 메일 인증 전 추천 대상이 아니고, 후보는 인증된 소개팅 신청 이성으로 제한했다. 노출 이력은 회원·후보별 UNIQUE로 남기고 매칭된 후보가 현재 카드에서 빠지면 미노출 후보로 채운다
- `photoId`를 발급해 S3 키는 서버에만 저장하고, 프로필 등록 때 회원 소유와 실제 업로드를 확인한다. 잠긴 후보 필드의 값·사진 URL·연락처는 응답에 없다
- 추천 응답의 `fields`는 고정 필드 DTO로 선언해 Swagger에 `additionalProp` 예시 키가 나타나지 않게 했다
- Swagger 프로필 요청 예시를 동국대 메일과 전화번호/인스타그램 2종으로 정리했다. 예시 `photoId`는 실제 발급값으로 교체해야 한다
- Swagger 내 프로필 응답의 `candidateId`(프로필)와 `photoId`(사진)에 서로 다른 예시 UUID와 설명을 붙였다
- 프론트 전달을 위해 `api-spec.md` §10을 요청·응답 JSON과 사진 업로드 순서로 다시 작성하고, 실제 S3 연동·학교 인증이 미검증/미연동임을 분리해 명시했다
- 소개팅 프로필과 사주 결과는 `member_id`로 간접 연결한다. `dating_profile.result_id` 직접 FK를 제거해 사주 결과의 계정 연결은 `result.member_id` 하나로 유지했다
- PostgreSQL Testcontainers로 V15→V16·JPA 매핑·추천 이력/보충·JWT 경로를 검증했고 전체 `./gradlew test --offline` 통과

**건드린 파일/패키지**
- `dating/` 신규 (Controller, 프로필·사진·추천 서비스, 엔티티·Repository·DTO, 인증 경로 등록), `result/ResultRepository.java` 후보 결과 일괄 조회 메서드
- `db/migration/V15__add_dating_profile_and_recommendation.sql`, `db/migration/V16__remove_dating_profile_result_id.sql`, `common/exception/ErrorCode.java`, `docs/plan.md`, `docs/api-spec.md`, `docs/handoff.md`, `dating/` 테스트

**다음 사람이 알아야 할 것**
- 이 작업은 #84 브랜치 `feat/84-dating-profile-recommendations`의 기본 작업 폴더(`/Users/seonwoo-choi/IdeaProjects/WKS-BE`)에 있다. 원래 #57 브랜치는 보존했다
- 리롤/해금/요청 API는 아직 없다. 리롤 비용·무료 횟수(TBD-6), 요청 비용(TBD-5), 학교 메일 인증 흐름(TBD-16)이 미결정이고 wallet/·학교 인증 구현이 필요하다
- 학교 이메일 인증 담당은 `DatingProfile.markVerified`를 연결해야 추천이 열린다. `GET /api/me`의 `hasDatingProfile`은 아직 연결 전이다. 소개팅 카드 등급 문구는 화면 고정이므로 추천 응답에 `tier`를 넣지 않는다
- V15와 V16은 V12(원장)·V13·V14가 순서대로 머지된 뒤에만 머지한다. V15를 로컬에 적용한 뒤 `result_id` 직접 FK를 제거하게 되어 V15 원본을 유지하고 V16으로 분리했다. 공용 파일 변경 팀 채널 공지는 사용자 요청으로 생략했다
- 로컬 PostgreSQL에는 V12 없이 V15가 먼저 적용돼 있었다. V15 원본 복원 후 V16을 적용해 `bootRun` 기동을 확인했다. 무시되는 `application-local.yml`에 `spring.flyway.out-of-order: true`를 로컬 한정으로 넣어 V12가 나중에 합쳐져도 적용되게 했다. V12 적용 후 이 설정은 제거한다. 운영/개발 서버는 V12→V15→V16 순서로 머지해야 한다

**막힌 것 / 넘기는 것**
- #84 PR을 올리고 리뷰받아야 한다. 학교 이메일 인증·wallet/ 연동은 곽도윤 담당
- S3 실제 버킷·권한이 없어 사진 업로드의 외부 왕복은 검증하지 못했다. 테스트에서는 외부 호출을 하지 않았다

**문서 변경**
- `docs/plan.md` §7.3·TBD 정리, `docs/api-spec.md` §1·§10, `docs/handoff.md`

**프론트에 알려야 할 것**
- `docs/api-spec.md` §10의 `photoId` 기반 프로필 요청과 Top 3 후보 응답. 해금·리롤·요청 API는 뒤 PR에서 추가


### 2026-09-23 (수) · 차은호 · saju/ + result/ 나와 잘 맞는 오행 + 이유 (#82 → PR #83) · Claude Code

**한 일**
- 피그마 사주 결과 화면의 "나와 잘 맞는 오행은 토(土)" 구획(기능명세 3.5·5.13) 데이터를 결과 응답에 넣었다: `elementMatch: { element, korean, reason }`
- 오행은 코드가 정한다: `LuckyPlace.luckyElement`(행운의 장소와 같은 보완 오행, 사람마다 고정) 를 public 으로 열어 재사용. 기획에 별도 규칙이 없어서 이렇게 잡았다 — **기획 확인 필요**
- 이유 문장은 기존 사주 해석 Gemini 호출에 `elementMatch` 필드 하나를 얹어 **한 번에** 받는다 (FR-GM-02). 프롬프트 입력에 "잘 맞는 기운: 흙 (나를 살려 주는 기운)" 한 줄 추가 — 그 기운이 나에게 무슨 뜻인지(비겁·식상·재성·관성·인성을 쉬운 말로)도 코드가 넘긴다
- `reading.element_match_content` (V14, nullable). 옛 행은 NULL → 응답 `elementMatch: null` → 화면 미노출. 프롬프트가 바뀌어 `analysisVersion` 이 달라지므로 같은 입력도 다시 생성된다 (#62)
- plan §8.2 의 별도 엔드포인트 `GET /api/results/{resultId}/element-match` 대신 결과 조회에 포함했다 (호출 1회, 5.13 자동 충족). plan 은 안 고쳤다 — 기획이 원본이라 기획 쪽에서 반영해 주면 좋겠다

**건드린 파일/패키지**
- `saju/`: `Reading`(필드 추가), `ReadingGenerator`(프롬프트 한 줄·필드 하나), `LuckyPlace.luckyElement` public, `prompts/reading-system.txt`
- `result/`: `ResultAnalysisPort.AnalysisResult`(필드), `SajuResultAnalysisAdapter`, `entity/Reading`(컬럼·생성자), `ResultService`(생성자 인자·`toAnalysis`), `dto/ResultResponse`(`elementMatch` + `ElementMatchResponse`)
- `db/migration/V14__add_reading_element_match.sql`
- 테스트: `ReadingGeneratorTest`(프롬프트 기대값), `SajuResultAnalysisAdapterTest`·`ResultServiceTest`(생성자·`elementMatch` 단언)

**다음 사람이 알아야 할 것**
- **#81(V13) 다음에 머지한다.** 이 브랜치는 `feat/79-compatibility-reason` 에서 땄다 (`GeminiJson` 의존)
- `SharedResultResponse`(공유 페이지) 에는 안 넣었다. 5.13 은 공유 유입자의 *자기* 결과(= 일반 홈)라 `ResultResponse` 로 충족
- 기존 저장 결과는 `elementMatch: null`. 축제 전 운영 DB 결과가 적으면 무시, 많으면 재생성 여부 결정

**막힌 것 / 넘기는 것**
- 기획: 잘 맞는 오행 선정 규칙이 보완 오행이 맞는지 확인. 아니면 `LuckyPlace.luckyElement` 대신 다른 함수로 바꾸면 된다 (응답·프롬프트 구조는 그대로)
- 차은호: #81 머지 뒤 PR #83 base 를 `dev` 로 변경. 팀 채널 공지(`result/` 관통 변경·V14)
- 운영: 이 PR 배포 전에 만든 결과는 `elementMatch: null`. 운영 DB 결과가 많으면 재생성 여부 결정 (같은 입력 재생성은 버전이 달라 LLM 을 다시 부른다)
- 로컬 end-to-end 에서 옛 행 NULL 경로는 앱이 먼저 꺼져 못 봤다. 단위 테스트(`ResultServiceTest.oldReadingWithoutElementMatchReasonYieldsNullSection`)로 대신 고정

**문서 변경**
- `docs/api-spec.md` (§2·§3 `elementMatch`), `docs/architecture.md` (§6 파이프라인), `docs/handoff.md`

**프론트에 알려야 할 것**
- 위 표 2026-09-23 행 (`elementMatch`)

### 2026-09-23 (수) · 차은호 · saju/ + compatibility/ 궁합 상세 이유 (#79 #80 → PR #81) · Claude Code

**한 일**
- 궁합지도 상세 시트의 세 질문("왜 나에게 귀인일까요?"·"둘이 만나게 된다면?"·"둘이 싸우게 된다면?")을 Gemini 한 번 호출로 만드는
  `saju/CompatibilityReasonGenerator` + `prompts/compatibility-reason-system.txt` 추가. 피그마(4.1.2 궁합 자세히 보기)의 구획당 2~4문장에 맞춰 각 3~4문장·150자 이내
- `GET /api/compatibilities/{id}/reason` 추가 (`CompatibilityReasonService`·`CompatibilityReasonController`). 처음 열 때 생성해 `compatibility.reason_*` 3컬럼(V13)에 캐싱, 재조회는 LLM 0회
- Gemini 호출부를 `saju/GeminiJson` 으로 뽑아 `ReadingGenerator` 와 공유. `CallBudget` 도 하나라 사주 해석·궁합 이유가 같은 한도를 쓴다 (FR-CP-14)
- 궁합 응답(`POST …/compatibility`)과 결과 조회의 `compatibilities[]` 에 `id` 추가 (프론트가 reason 을 부르려면 필요. 추가만이라 계약 위반 아님)
- `ErrorCode.COMPATIBILITY_NOT_FOUND`(404) 추가, `CompatibilityTier.korean()` 추가
- 로컬 postgres 로 end-to-end 확인: V13 적용·`validate` 통과, 결과 2개 → 궁합 → reason 첫 호출 2.3초(Gemini 1회) → 재호출 10ms(캐시) → 없는 id 404. 생성 문장은 상생 방향(흙→쇠)이 입력과 일치했고 "당신" 없이 두 사람을 기운으로 가리켰다

**건드린 파일/패키지**
- `saju/`: `GeminiJson`(신규), `CompatibilityReasonGenerator`(신규), `CompatibilityReason`(신규), `ReadingGenerator`(호출부 분리, 프롬프트 조립은 그대로)
- `compatibility/`: `CompatibilityReasonService`·`CompatibilityReasonController`·`dto/CompatibilityReasonResponse`(신규), `CompatibilityRepository`(`findByIdWithResults`·`saveReasonIfAbsent`), `entity/Compatibility`(3컬럼), `entity/CompatibilityTier`(`korean()`), `dto/CompatibilityResponse`(`id`)
- `result/dto/ResultResponse.CompatibilityResponse`(`id`), `common/exception/ErrorCode`, `db/migration/V13__add_compatibility_reason.sql`, `resources/prompts/compatibility-reason-system.txt`
- 테스트: `CompatibilityReasonGeneratorTest`·`CompatibilityReasonServiceTest`(신규), `ReadingGeneratorTest`·`GeminiSmokeTest`·`CompatibilityControllerTest`(생성자 변경 반영)

**다음 사람이 알아야 할 것**
- **궁합 이유 프롬프트에는 성별을 넣지 않는다.** 두 사람이 같은 글을 보고, 성별을 고려한 글은 소개팅 쪽이 따로 만든다 (2026-09-23 결정)
- `CompatibilityReasonService.getReason` 은 일부러 트랜잭션이 없다. LLM 30초를 트랜잭션 안에서 기다리면 공유가 몰릴 때 커넥션 풀이 마른다. 동시 최초 열람은 `UPDATE … WHERE reason_why IS NULL` 로 먼저 온 쪽만 저장되고 진 쪽은 다시 읽는다
- 프롬프트를 바꿔도 기존 캐시는 옛 글로 남는다(버전 컬럼 없음). 다시 만들려면 `reason_*` 를 NULL 로
- **최선우·곽도윤:** `compatibility/`·`result/dto`·`ErrorCode` 를 건드렸다. PR 리뷰에서 봐 주면 좋겠다. 이유 API 담당이 "미정"이라 내가 가져갔다
- 피그마 사주 결과 화면의 **"나와 잘 맞는 오행 + 이유"(기능명세 3.5)** 는 같은 날 #82 → PR #83 으로 따로 했다 (위 기록)
- 피그마의 연애운·결혼운·자녀운 본문은 6문장 안팎으로 보이는데 현재 프롬프트는 8~10문장이다. 기획 확인 후 줄일지 결정 (위 "지금 막혀 있는 것" 표)
- 리뷰어: 최선우(`seonwoochoi24`)·곽도윤(`hairyung2002`) 지정함
- 로컬 확인 때 `wks-postgres` 컨테이너에 옛 데이터가 있어 V10~V13 만 새로 적용됐다. 빈 DB 에서 V1 부터 도는 것은 이번에 안 봤다

**막힌 것 / 넘기는 것**
- 차은호: `ErrorCode`·`db/migration/`·`compatibility/` 변경 **팀 채널 공지** (AGENTS.md 규칙, 아직 안 함)
- 프론트: `id` 필드 2곳 + reason 엔드포인트 공지 (위 "프론트에 공지한 API 변경" 표, 미전달)

**문서 변경**
- `docs/api-spec.md` (§1 에러 코드, §3 `compatibilities[].id`, §4 `id` + `GET /api/compatibilities/{id}/reason`), `docs/architecture.md` (§6 궁합 이유), `docs/handoff.md`

**프론트에 알려야 할 것**
- 위 "프론트에 공지한 API 변경" 표 2026-09-23 행. `id` 필드 추가 2곳 + reason 엔드포인트

### 2026-09-23 (수) · 곽도윤 · 인프라: 운영 compose·main 배포 트리거 diff 승인·적용 · Claude Code

**한 일**
- 바로 아래 기록(같은 날, "인프라 파일 준비")에서 diff로만 제시했던 두 변경을 본인이 확인 후 승인해
  실제로 적용했다:
  - `docker-compose.prod.yml`: `postgres`·`app`·`nginx` 세 서비스에 `wks-edge`(external) 네트워크 추가,
    `nginx`에 `./nginx/api-dev.conf.active:/etc/nginx/conf.d/api-dev.conf:ro` 볼륨 마운트 추가, `app`에
    `JAVA_TOOL_OPTIONS`(`-Xmx300m -XX:MaxMetaspaceSize=128m`)와 로그 rotate(json-file, max-size 10m,
    max-file 3) 추가. `certbot` 서비스는 wks-edge에 안 붙였다(네트워크로 다른 컨테이너와 통신할 필요가
    없음 — named volume으로만 nginx와 인증서를 공유)
  - `.github/workflows/deploy.yml`: 트리거를 `push: branches: [dev]` → `[main]`으로 변경. 그 외(이미지
    태그 `:latest`, 배포 경로, concurrency 그룹)는 그대로 유지
- 더미 값으로 `docker compose -f docker-compose.prod.yml config` 통과 확인, `deploy.yml` YAML 파싱 확인
- `docs/git-workflow.md`·`docs/runbook-dev-server.md`의 "diff 승인 대기" 문구를 "적용 완료"로 갱신하고,
  **머지 직후 공백**을 명시했다: `deploy.yml` 트리거 변경이 `dev`에 머지되는 순간부터 `dev` push는 더 이상
  운영을 배포하지 않는데, `main`으로 릴리즈해본 적이 아직 한 번도 없어서 **첫 `dev`→`main` PR을 병합하기
  전까지는 운영에 새 커밋이 전혀 배포되지 않는 공백이 생긴다.** 기존 컨테이너는 계속 떠 있으니 서비스
  중단은 아니지만, 이 공백 동안은 핫픽스 자동 배포가 안 된다

**건드린 파일**
- `docker-compose.prod.yml`, `.github/workflows/deploy.yml`, `docs/git-workflow.md`, `docs/runbook-dev-server.md`

**다음 사람이 알아야 할 것**
- **이 변경들은 로컬 작업 트리에만 있고 아직 커밋되지 않았다.** 다른 미커밋 작업(auth/member 등, 위쪽
  기록 참고)과 섞이지 않게 인프라 변경만 따로 브랜치를 따서 커밋·PR 올릴 것
- `docker-compose.prod.yml`을 EC2에 실제로 반영하면(runbook 2-2단계) 운영 컨테이너 3개가 재생성되며
  약 30~60초 다운타임이 생긴다 — 그 시점에 `nginx/api-dev.conf.active` 파일이 EC2에 먼저 있어야 한다
  (runbook 2-1, 없으면 Docker가 빈 디렉터리를 만들어 nginx가 기동 실패한다)
- **머지 후 되도록 빨리 첫 `dev`→`main` 릴리즈 PR을 만들어 병합할 것.** 안 그러면 운영이 자동 배포 공백
  상태로 남는다 (위 "한 일" 참고)

**막힌 것 / 넘기는 것**
- 곽도윤: 이 변경들 커밋·PR, `docs/runbook-dev-server.md` 실제 EC2 실행, 첫 `dev`→`main` 릴리즈 PR

**문서 변경**
- `docs/git-workflow.md`, `docs/runbook-dev-server.md`, `docs/handoff.md`(이 항목)

**프론트에 알려야 할 것**
- 없음

### 2026-09-23 (수) · 곽도윤 · 인프라(EC2 운영·개발 서버 nginx 분리) 파일 준비 · Claude Code

**한 일**
- 하나의 EC2에서 `api.threadoffate.site`(운영)·`api-dev.threadoffate.site`(개발)를 nginx server_name으로
  나누는 구성을 준비했다. **EC2 원격 접속·명령 실행은 하지 않았다** — 레포 파일만 만들었고, 실행은 본인이
  `docs/runbook-dev-server.md` 순서대로 한다
- 현재 구조 확인 결과: `dev` push가 **이미 운영에 배포되고 있다** (`deploy.yml` 트리거가 `dev`).
  `main` 트리거 워크플로는 없다 — 이번 작업 브리핑이 "main이 이미 운영 배포 중"이라고 가정했던 부분과
  실제가 달라서, 트리거를 `dev`→`main`으로 바꾸는 `deploy.yml` 변경은 **diff만 만들고 적용하지 않았다**
  (아래 "막힌 것" 참고 — 팀 전체 릴리즈 방식이 바뀌는 결정이라 확인 필요)
- postgres 계정 격리(`db/init/init-wks-dev.sql`)를 로컬 postgres:16 컨테이너로 직접 검증하다가, `wks`가
  이 postgres 컨테이너의 슈퍼유저라 `REVOKE CONNECT`가 `wks_dev → wks` 방향만 막고 `wks → wks_dev`는
  못 막는다는 걸 확인했다. 더 중요한 방향(개발 환경에서 운영 개인정보 접근)은 막힌다 — SQL 주석·runbook에 반영
- `.env.dev.example`에 값 뒤에 인라인 `#` 주석을 썼다가 `docker compose config`로 직접 검증하는 과정에서
  주석이 값 문자열에 그대로 붙는 걸 발견해 고쳤다 (`.env` 파일은 인라인 주석을 지원하지 않는다 — 값 앞
  줄에 주석을 둬야 한다)
- nginx 설정 2종(bootstrap 80번 전용 / 최종 80+443)은 로컬에서 `docker run nginx:1.27-alpine nginx -t`로
  문법·인증서 로딩까지 검증함(더미 자체서명 인증서로 확인). `proxy_pass` 대상 컨테이너가 로컬 테스트
  네트워크엔 없어서 "host not found in upstream"이 나오는데, 이건 기존 `nginx/default.conf`도 로컬 단독
  테스트에서 똑같이 나는 현상이라 문법 문제가 아님을 대조 확인함
- **`.env.prod.example`에 실제 비밀값이 있는 걸 다시 확인했다** — 이미 위 표·2026-09-23 다른 기록에 있는
  것과 같은 항목. 본인이 이미 보류 결정을 내린 사안이라 추가 조치는 안 함, 재확인만

**건드린/새로 만든 파일**
- `nginx/api-dev.bootstrap.conf`(신규), `nginx/api-dev.conf`(신규) — 기존 `nginx/default.conf`는 **안 건드림**
- `docker-compose.dev.yml`(신규, `/opt/wks-dev`용), `.env.dev.example`(신규)
- `db/init/init-wks-dev.sql`(신규) — Flyway 아님, 수동 1회 실행용
- `src/main/resources/application-dev.yml`(신규)
- `.github/workflows/deploy-dev.yml`(신규), `.github/workflows/ci.yml`(신규, PR 빌드+테스트)
- `docs/runbook-dev-server.md`(신규) — EC2에서 실행할 순서. ⚠️ 운영 영향 단계 표시, 각 단계 확인·롤백 포함
- `docs/git-workflow.md` — CI/CD 매핑 표를 확정된 구조로 갱신, 과도기(트리거 미전환 구간) 위험 명시
- **아직 안 건드림(승인 대기)**: `docker-compose.prod.yml`(wks-edge 네트워크 추가), `.github/workflows/deploy.yml`(트리거 `dev`→`main`) — diff는 세션 응답에 남겨둠, 이 파일엔 미반영

**다음 사람이 알아야 할 것**
- **`docker-compose.prod.yml`·`deploy.yml` 두 파일은 diff만 있고 적용 안 됐다.** 적용하면 운영 컨테이너
  3개(postgres·app·nginx)가 재생성되며 약 30~60초 다운타임이 생긴다 (`docs/runbook-dev-server.md` 2-2단계)
- **`deploy.yml` 트리거를 바꾸기 전까지는 `dev` push가 여전히 운영을 배포한다.** `deploy-dev.yml`이 먼저
  머지되면 `dev` push 한 번이 운영·개발 양쪽에 동시 배포되는 과도기가 생긴다 — `git-workflow.md`에 명시함
- nginx 인증서 발급 전에 443 블록을 먼저 넣으면 운영까지 같이 죽는다 — runbook 5~7단계 순서(80번 먼저 →
  인증서 발급 → 443번)를 반드시 지킬 것. 볼륨 마운트 파일(`nginx/api-dev.conf.active`, git 비추적)을
  네트워크 변경 전에 미리 만들어둬야 하는 이유도 runbook 2-1에 적어둠(안 그러면 Docker가 빈 디렉터리를
  대신 만들어 nginx가 기동 실패한다)
- 카카오 디벨로퍼스에 개발용 앱을 별도로 만들어 Redirect URI(`https://dev.threadoffate.site/...`, 로컬
  콜백 포함)를 등록해야 `.env.dev.example`의 `KAKAO_CLIENT_ID`/`SECRET`을 채울 수 있다 — DNS 제외하고
  이 범위 밖 수동 작업

**막힌 것 / 넘기는 것**
- 곽도윤: `docker-compose.prod.yml`·`deploy.yml` diff 검토·승인, 승인 후 이 세션이나 다음 작업에서 적용
- 곽도윤: `docs/runbook-dev-server.md` 실제 실행(이 세션은 EC2를 건드리지 않음), Route53 A레코드, 카카오
  개발용 앱 등록, GitHub Secrets는 기존 것(`EC2_HOST` 등) 재사용이라 신규 추가 없음(재확인 요망)
- 팀: `deploy.yml` 트리거 전환 시점 — 전환하면 그날부터 운영 릴리즈가 `dev`→`main` PR 머지 방식으로
  바뀐다는 걸 전원이 알아야 함

**문서 변경**
- `docs/git-workflow.md`(CI/CD 매핑 표, GitHub Secrets 절), `docs/handoff.md`(이 항목)

**프론트에 알려야 할 것**
- 없음 (백엔드 배포 인프라 변경, API 계약 변경 없음). 개발 서버 도메인(`api-dev.threadoffate.site`)이
  뜨면 프론트가 dev 환경에서 쓸 base URL로 공유 가능

### 2026-09-23 (수) · 곽도윤 · auth/·member/·common/auth/ 카카오 로그인 로컬 구현·검증 + 로그아웃 추가 · Claude Code

**한 일**
- 2026-09-21 문서화 단계였던 카카오 로그인을 실제로 구현하고 로컬에서 왕복 검증했다. `auth/`(AuthController·AuthService·KakaoClient·KakaoConfig·RedirectUriPolicy)·`common/auth/`(JwtProvider·JwtAuthInterceptor·CurrentMemberArgumentResolver·AuthWebConfig)·`member/`(Member·MemberService·MeController·MeService) 전부 이번에 처음 로컬 검증됨. `ErrorCode` 에 `UNAUTHENTICATED`·`KAKAO_UNAVAILABLE` 추가돼 있었다
- 카카오 디벨로퍼스 콘솔 설정 완료: REST API 키·Client Secret 발급, 카카오 로그인 활성화. **Redirect URI 는 5173 이 아니라 3000 이다** — `docs/plan.md`/ADR 이 Vite 기본 포트(5173)를 가정했지만 프론트 `vite.config.ts` 는 포트를 3000 으로 고정해뒀다(WKS-FE `docs/phases/03-saju-reading/RESULT.md` 가 2026-09-15 에 이미 이 불일치를 기록해 뒀었음). 실제 등록값: `http://localhost:3000/dev/kakao-callback`
- 왕복 검증(로컬): `/dev/kakao` → 카카오 인가 → 콜백 → `isNewUser`/`restoredResultId` 정상, `GET /api/me` 200(토큰 있음)/401 `UNAUTHENTICATED`(토큰 없음), DB `member` 테이블에 `kakao_id` 만 저장됨(개인정보 없음, AGENTS.md 규칙 준수) 확인. 사주 결과 생성(`POST /api/results`)도 실제 Gemini 키로 성공 확인 — 계정 결과 연결·복원(`restoredResultId`)도 실제 UUID로 동작 확인
- **로그아웃 추가 (2026-09-21 "로그아웃 없음" 결정을 일부 뒤집음, 사용자 본인 지시).** 서버 쪽 토큰 폐기는 여전히 없다 — 클라이언트가 로컬 토큰을 지우는 방식뿐이다(`features/auth/LogoutButton.tsx` → `clearAuthToken()`). 지우기 전 사본은 만료까지 유효함을 화면에 안내 문구로 명시
- JWT 만료를 **30일 → 15일**로 줄였다(`application.yml` `app.auth.jwt.ttl-days`). refresh token 은 미구현 — 카카오 재로그인이 원클릭이라 마찰이 적다고 판단(축제 특성상 사용 기간도 짧음)
- 프론트 로컬 저장 필드명을 `token` → `accessToken` 으로 바꿨다(`wks:auth` 의 내부 필드만, API 응답 필드 `KakaoLoginResponse.token` 은 그대로 — 프론트-백엔드 계약은 안 건드림)
- **`.env.prod.example` 에 실제 비밀값(DB 비번·Gemini 키·카카오 client-id/secret)이 들어갔다.** 정리하자고 제안했으나 본인이 "private 저장소라 상관없다"며 보류 결정. 위 "막힌 것" 표에 남겨둠 — public 전환 논의 시 반드시 재확인할 것
- 로컬 `application-local.yml`에 카카오 client-id/secret·JWT secret·Gemini 실키를 채워 넣었다(이 파일은 `.gitignore` 대상이라 커밋 안 됨)

**건드린 파일/패키지 (백엔드, 전부 미커밋)**
- `application.yml`(`app.auth.jwt.ttl-days: 15`), `application-local.yml`(로컬 전용, 커밋 안 됨), `common/auth/JwtProvider.java`(주석), `docs/api-spec.md`·`api.md`·`docs/architecture.md`·`docs/backend-requirements.md`(만료 30→15일, 로그아웃 문구 정정), `docs/handoff.md`
- `auth/`·`common/auth/`·`member/` 자체는 이번 세션 이전부터 로컬에 있던 것(누가 작성했는지는 이 세션에서 확인 못 함) — 이번에 처음으로 실제 기동·검증함

**건드린 파일/패키지 (프론트, WKS-FE, 전부 미커밋)**
- `src/api/authToken.ts`(필드명 `token`→`accessToken`, 주석), `src/api/authToken.test.ts`
- `src/features/auth/LogoutButton.tsx`(신규)·`LogoutButton.test.tsx`(신규)·`index.ts`(export 추가)
- `src/app/dev/KakaoLoginTestPage.tsx`(로그인 상태면 로그아웃 버튼 토글)·`KakaoLoginTestPage.test.tsx`
- `docs/decisions/ADR-20260922-kakao-login-and-jwt-session.md`(Amendment 섹션 추가: 포트 정정, 로그아웃, TTL, 필드명)
- `.env.local`(신규, 커밋 안 됨) — `VITE_KAKAO_CLIENT_ID`

**다음 사람이 알아야 할 것**
- **아직 아무것도 커밋되지 않았다.** 백엔드는 현재 `Fix/#54/PreRegistration` 브랜치 위에 사전신청 변경(V10 개명 등)과 이번 auth 작업이 섞여 있다 — `dev`에서 새 브랜치를 따서 auth 만 분리해 커밋할 것. 프론트도 별도 브랜치/PR 필요
- **redirect URI 는 3000 이다, 5173 이 아니다.** ADR·이전 기록의 5173 언급은 틀렸다(수정은 ADR Amendment 에 반영함). 운영 배포 시에도 실제 프론트 배포 포트/도메인 기준으로 카카오 콘솔·`allowed-redirect-uris` 를 맞출 것
- **로그아웃은 클라이언트 전용이다.** 서버는 토큰을 폐기하지 않는다 — 로그아웃 버튼을 눌러도 탈취된 토큰 사본은 만료(15일)까지 유효하다. 진짜 서버 측 무효화(jti 블록리스트 등)가 필요하면 별도 작업(새 DB 테이블·V13 마이그레이션 필요)
- **JWT 라이브러리(`nimbus-jose-jwt`) 팀 공지가 아직 안 됐다.** convention.md 규칙상 커밋 전에 팀 채널에 알릴 것
- `.env.prod.example` 에 실제 비밀값이 남아 있다 — 위 "막힌 것" 표 참고
- Gemini 키는 2026-09-17 #72 사고 때 남았던, 아직 폐기 안 한 그 키를 로컬 테스트에 그대로 재사용했다. 운영 배포 전 재발급 필요는 그대로 유효

**막힌 것 / 넘기는 것**
- 팀: JWT 라이브러리 승인 공지, 브랜치 분리·PR
- 곽도윤: `.env.prod.example` 정리 여부(본인이 보류 결정했으나 재확인 필요), Gemini 키 폐기·재발급

**문서 변경**
- `docs/api-spec.md`(§9 만료·로그아웃 문구), `api.md`(같음), `docs/architecture.md`(JWT 절), `docs/backend-requirements.md`(§16 FR-AU-09 등), `docs/handoff.md`(이 항목, 현재 상태 표, 막힌 것 표, Flyway 표)
- WKS-FE: `docs/decisions/ADR-20260922-kakao-login-and-jwt-session.md` Amendment 섹션

**프론트에 알려야 할 것**
- 로그인 API 계약(`docs/api-spec.md` §9)은 안 바뀌었다(필드 삭제·타입 변경 없음) — `token` 필드명 그대로. 바뀐 건 프론트 로컬 저장 방식뿐이라 백엔드 관점에서 새로 공지할 계약 변경은 없음
- JWT 만료가 30일에서 15일로 줄었다 — 프론트가 어딘가에 30일을 하드코딩해 안내 문구를 썼다면 확인 필요

### 2026-09-21 (월) · 곽도윤 · 문서 (V1 기획 반영: 카카오 로그인·소개팅·실) · Claude Code

**한 일**
- `docs/plan.md`(V1 기획 원본)에 맞춰 문서를 정렬했다 (구현 전). 같은 날 오전에 쓴 문서에서 **정정된 것**:
  - 인증: 서버 사이드 세션 + Spring Security·Session JDBC → **프론트 주도 code 교환 + JWT** (`POST /api/auth/kakao`). Spring Security·Session JDBC·CSRF·쿠키·`spring_session`(V12) 계획은 폐기
  - 결과 연결: `member_result` 테이블·N개 저장 → **`result.member_id`(계정당 1개, 부분 unique)**. "`result` 에 `member_id` 금지" 규칙 폐기
  - 이름: plan.md 의 `user_id` → `member_id` (`user` 는 금지어이자 Postgres 예약어)
  - 소개팅이 `signup` 을 대체한다. **학교 메일 재학 인증은 유지**하고 매직링크를 재사용한다 (위치·저장 미정, TBD-16)
- 결정 (2026-09-21): JWT 채택(만료 30일·갱신 없음), 로그아웃·기기 관리·토큰 폐기·**탈퇴 없음**(삭제 요청은 운영자 수동 처리) / 카테고리는 api-spec 기준(`MARRIAGE`·`CHILDREN`·`LOVE`)이며 plan §3.8 의 변경 문구는 오기 / 궁합 id 는 순번 그대로(열거 위험 수용) / 원장 `ref_id NOT NULL` / 축제 09-29 ~ 10-01 확정 / plan.md 에 없던 3가지를 보완한 부분(아래 참고)은 그대로 유지
- `docs/plan.md` 수정: `user_id`→`member_id`, 로그인 요청에 `resultId?` 추가, JWT 명시, ledger `ref_id NOT NULL`, §3.8 정정, TBD-3·12·17 종료, TBD-13~16 추가, §12 에 로그아웃·탈퇴 추가 (수정한 곳에 날짜 표시)
- Flyway 번호 정리: `V9__add_signup_photo_key.sql` → **`V10`** 으로 개명 완료(`git mv`, PR 은 곽도윤이 올린다). 로그인은 V11, 원장은 V12 로 예약
- 루트 `AGENTS.md` 를 작성했다 (이전엔 0바이트였다). 도구가 달라도 읽는 규칙 원본이다
- 로그인 API 초안을 `docs/api-spec.md` §9 와 `api.md` §6 에 추가했다 (`POST /api/auth/kakao`, `GET /api/me`). §5 사전등록에는 "소개팅 프로필로 대체 예정" 표시
- 로그인 API 에 `GET /api/me/result`(토큰 기반 내 결과 조회)를 추가했다. 브라우저가 `resultId` 를 잃어도 로그인 상태면 복원할 수 있다. `plan.md` §8.1·architecture·requirements(FR-AU-13)에도 반영. `KAKAO_UNAVAILABLE`(503)은 확정
- `api.md` 를 `api-spec.md` 와 맞췄다 (`GET /api/results/{id}/input`·`PATCH` 누락, 응답의 `elements` 누락, `destiny.title` 문구). 줄바꿈은 다른 문서처럼 CRLF 로 통일
- `docs/git-workflow.md` 를 실제에 맞게 정정: `dev` push 가 운영 배포, 배포 파이프라인이 테스트를 건너뜀, PR CI 없음, 브랜치 보호 불가, GitHub Secrets 목록
- `common/agents.md`("인증 체인이 없다" 문구)와 `docs/git-workflow.md`(이슈 라벨에 `auth`·`member`·`dating`·`wallet`) 정정

**건드린 파일/패키지**
- 코드 변경 없음. `docs/architecture.md`, `docs/convention.md`, `docs/backend-requirements.md`(§16 재작성, §17 신설), `docs/handoff.md`, `docs/plan.md`

**다음 사람이 알아야 할 것**
- **문서만 바뀌었고 코드는 그대로다.** 로그인 API 는 `api-spec.md` §9·`api.md` §6 에 **초안**이 있다 (구현 PR 에서 확정, `ErrorCode` 도 그때 추가). 소개팅·실 API 는 명세 확정 전이라 아직 없다. `KAKAO_UNAVAILABLE`(503)은 2026-09-21 확정했다
- **JWT 라이브러리는 미정이고 팀 승인이 필요하다.** Boot 4 는 Jackson 3 이라 Jackson 2 에 의존하는 라이브러리는 공존 여부를 확인할 것. 만료는 30일·갱신 없음으로 확정
- **`V9` 중복은 해소했다.** `V9__add_signup_photo_key.sql` 을 `V10__add_signup_photo_key.sql` 로 개명했다 (dev 에는 V9 가 하나뿐이라 머지해도 기동에 문제없다). **이 브랜치로 로컬 DB 를 이미 기동해 예전 V9(photo_key)를 적용했다면 Flyway 이력이 어긋난다.** 로컬 DB 를 초기화(`docker compose down -v`)하거나 `flyway repair` 후 재기동할 것. 운영은 dev 브랜치 기준으로 배포되고 dev 에는 사진 마이그레이션이 없어서 영향 없다
- **`.env.prod.example`(git 추적 중)에 실제 값으로 보이는 DB 비밀번호·Gemini 키가 있다.** #72 는 `application-prod.yml` 만 고쳤다. 폐기·재발급하고 플레이스홀더로 바꿔야 한다
- **`GET /api/compatibilities/{id}/reason` 의 id 는 순번이다** (수용된 위험). 열거로 남의 궁합 이유 열람·LLM 생성 유발이 가능하고, 총량은 `CallBudget` 이 막는다
- **PR 용 CI 가 없고 배포 파이프라인이 테스트를 건너뛴다.** `deploy.yml` 은 `bootJar -x test` 로 이미지를 만들어 `dev` push 마다 운영에 배포한다. 머지 전에 로컬에서 `./gradlew test` 를 돌릴 것. NFR-T-07·TR-E-05(CI 필수)는 아직 충족되지 않았다
- plan.md 에서 비어 있던 부분을 채운 것(기획 확인 필요): 연결한 경우 `restoredResultId` 는 `null`, 존재하지 않는 `resultId` 로도 로그인은 성공, 이미 다른 회원에 연결된 결과는 연결하지 않음
- 이번 범위 밖이라 **고치지 않고 남긴 문서 불일치**: architecture §5 의 `signup` DDL 이 V7·photo_key 를 반영하지 않음 / "`name`·`phone` 컬럼은 없다" 문구가 V7 이후 사실과 다름 / `TraceIdFilter` 가 architecture 에 있으나 코드에 없음 / `common/agents.md` 의 "인증 체인이 없다" 문구가 JWT 도입 후 사실과 다름

**막힌 것 / 넘기는 것**
- 팀: JWT 라이브러리 승인, `auth/`·`member/`·`wallet/`·`dating/` 담당 확정
- 곽도윤: 카카오 디벨로퍼스 콘솔(redirect URI 는 프론트 콜백), `.env.prod.example` 정리, #54 PR 생성 (V10 개명 완료)
- 기획: plan.md TBD-13~16(궁합 캐시 위치, 로그인 상태 결과 연결·5.8 중복 방지, 성별·선호성별, 학교 메일 인증 위치), 데이터 파기 범위, 처리방침 개정(삭제 요청 창구 포함)

**문서 변경**
- `docs/architecture.md`(머리말·§1·§2·§3·§4·§5·§6·§8·§9), `docs/convention.md`(용어·금지어·인증·로깅·테스트·AI 표), `docs/backend-requirements.md`(§2·§3·§10·§11·§15·§16·§17), `docs/handoff.md`, `docs/plan.md`, **`AGENTS.md`(신규 작성)**, `docs/api-spec.md`(§1·§5·§8·§9), `api.md`(§1·§4·§6), `src/main/java/com/darkness/wks/common/agents.md`, `docs/git-workflow.md`, `src/main/resources/db/migration/V10__add_signup_photo_key.sql`(개명)

**프론트에 알려야 할 것**
- 로그인 API 초안이 `api-spec.md` §9 / `api.md` §6 에 있다 (**구현 전, 확정 아님**). 공지 표는 아직 ❌ 다. 프론트에서 받아야 할 것: 콜백 주소(운영·로컬·netlify). 사전 예고: 사주·궁합·공유 API 는 바뀌지 않는다. 인증 API 는 `Authorization: Bearer` 이고 쿠키를 쓰지 않는다

### 2026-09-16 (수) · 곽도윤 · signup/ 사진 업로드 S3 연동 (#54) · Claude Code

**한 일**
- 9/15에 "사진 업로드는 2차 릴리즈로 보류"로 결정했던 걸 번복 — 기획 변경으로 이번 릴리즈에 사진도 선택값으로 받기로 함
- 업로드 방식은 **presigned URL**로 결정 (서버가 파일 바이트를 직접 받지 않음): `POST /api/signups/photo-upload-url`로 S3 PUT용 presigned URL·`photoKey` 발급 → 프론트가 S3에 직접 업로드 → `photoKey`를 기존 `POST /api/signups` 요청에 실어 보냄
- `software.amazon.awssdk:s3`(BOM 2.29.52) 신규 의존성 추가 — **convention.md의 "AI가 새 라이브러리 추천하면 일단 거절, 팀에 물어본다" 규칙에 걸려서 진행 전에 사용자(곽도윤 본인) 확인 받음**
- `common/config/S3Config.java` 신규 — `S3Client`/`S3Presigner` 빈. 자격증명은 하드코딩하지 않고 AWS 기본 자격증명 체인(`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` 환경변수 또는 EC2 IAM 역할)에 위임. `S3Client`는 `apiCallTimeout(5s)` 명시 (외부 호출 타임아웃 필수 규칙)
- `signup/PhotoUploadService.java` 신규 — `createUploadUrl(contentType)`: `image/jpeg`·`image/png`·`image/webp`만 허용, 아니면 `INVALID_INPUT`. 키는 `signup-photos/{UUID}.{ext}`. `verifyPhotoExists(photoKey)`: `SignupService.createSignup`에서 호출 — `photoKey`가 실제 S3에 없으면(미업로드·만료·위조) `INVALID_INPUT` 400으로 막음 (resultId 검증과 동일 패턴)
- `Signup` 엔티티·`CreateSignupRequest`에 `photoKey` 추가 (마지막 파라미터로 추가해서 기존 호출부는 `null` 하나만 붙이면 되게 함). `V9__add_signup_photo_key.sql`(원래 V8 예약, dev 병합 중 차은호의 V8과 충돌해 V9로 재번호) — `photo_key VARCHAR(255)` nullable 컬럼
- `SignupServiceTest`·`SignupControllerTest` 기존 생성자 호출부 전부 갱신(11번째 인자 추가) + `PhotoUploadServiceTest` 신규 + 사진 검증 성공/실패 케이스 `SignupServiceTest`에 추가. `./gradlew test --tests "com.darkness.wks.signup.*"` 통과 확인
- 새 `ErrorCode`는 추가하지 않음 — `INVALID_INPUT` 재사용 (resend의 "이미 인증됨" 케이스와 같은 패턴)이라 `common/ErrorCode.java`는 안 건드림

**건드린 파일/패키지**
- `build.gradle` — AWS SDK BOM + s3 모듈 추가
- `common/config/S3Config.java` (신규)
- `signup/PhotoUploadService.java` (신규), `signup/PhotoUploadServiceTest.java` (신규)
- `signup/dto/PhotoUploadUrlRequest.java`, `signup/dto/PhotoUploadUrlResponse.java` (신규)
- `signup/entity/Signup.java`, `signup/dto/CreateSignupRequest.java`, `signup/SignupService.java`, `signup/SignupController.java`
- `signup/SignupServiceTest.java`, `signup/SignupControllerTest.java`
- `db/migration/V9__add_signup_photo_key.sql` (신규)
- `application.yml`(`app.aws.*`), `.env.prod.example`, `docker-compose.prod.yml` — `AWS_REGION`·`AWS_S3_BUCKET`·`AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY` 추가
- `api.md` §4, `docs/api-spec.md` §5, `docs/backend-requirements.md` FR-SU-16, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- **실제 S3 버킷·IAM 자격증명이 아직 없다.** `AWS_S3_BUCKET`이 비어있으면(현재 기본값) 이 기능은 호출 시 에러난다 — 배포 전에 버킷 생성 + IAM 사용자(또는 EC2 역할) 발급 + `.env` 채우기 필요. 로컬에서 이 엔드포인트 테스트하려면 각자 로컬에 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_S3_BUCKET` 환경변수 설정 필요 (`application-local.yml.example`엔 안 넣었음 — 필수 아니라 기동엔 안 막힘)
- 버킷 CORS 설정도 필요함 (프론트가 브라우저에서 presigned URL로 직접 PUT 하려면 S3 버킷에 프론트 origin CORS 허용 필요) — **아직 안 함, 배포 전 확인**
- **서버가 업로드 용량 제한을 강제하지 않는다.** presigned PUT은 POST policy와 달리 `content-length-range` 조건을 못 건다 — 프론트가 파일 선택 시 용량 제한하거나, S3 버킷 정책/Lambda로 후처리 검증 필요 (FR-SU-16에 명시)
- `photoKey`는 선택값. 안 보내면 사진 없이 신청 가능 (기존 6개 프로필 필드와 동일하게 전부 nullable 기조 유지)
- presigned URL 만료는 10분(`app.aws.s3.presigned-url-ttl-minutes`) — 프론트가 URL 발급 후 오래 끌다 업로드하면 만료돼서 S3가 거부함. 재발급은 그냥 엔드포인트 재호출

**막힌 것 / 넘기는 것**
- S3 버킷 생성·IAM 자격증명 발급·버킷 CORS 설정 — AWS 콘솔 작업이라 코드로 대신 못 함. 배포 전 필수
- 사진 용량 상한이 기획 미정 — 정해지면 프론트 검증 로직에 반영 필요

**문서 변경**
- `api.md` §4, `docs/api-spec.md` §5, `docs/backend-requirements.md` FR-SU-16, `docs/handoff.md`

**프론트에 알려야 할 것**
- `POST /api/signups/photo-upload-url` 신규 — `{ contentType }` → `{ uploadUrl, photoKey, expiresInSeconds }`. `uploadUrl`에 파일을 `PUT`(Content-Type 헤더 동일하게) → `photoKey`를 `POST /api/signups`의 `photoKey` 필드에 실어 보내면 됨
- `photoKey`는 선택값 — 사진 없이도 신청 가능
- `api.md` §4 갱신함

### 2026-09-14 (월) · 차은호 · saju/ 등급 노출 완화 (#32) · Codex

**한 일**
- 해석 문장에 내부 운세 등급을 반드시 명시하지 않아도 되도록 시스템 프롬프트 규칙을 명확히 함
- 등급은 기존처럼 해석의 방향과 강도를 정하는 입력으로 유지

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- API 응답 구조와 등급 산출 로직은 바뀌지 않음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 곽도윤 · 사전신청 API 필드 확장 (피그마 반영) · Claude Code

**한 일**
- 프론트가 공유한 사전신청 화면(피그마, "가을 축제, 나에게 어떤 인연이 찾아올까?")과 `POST /api/signups` 계약을 대조 → 이름·연락처(전화/인스타)·학과·MBTI·자기소개가 API에 없는 걸 확인
- 9/13 기록에 "팀 결정 필요"로 남아있던 FR-SU-11("이름·전화번호 미수집")을 **정책 변경**으로 해결 — 피그마대로 수집하는 쪽으로 결정
- `Signup` 엔티티·`CreateSignupRequest`에 `name`·`contactMethod`(`PHONE`\|`INSTAGRAM`)·`contactValue`·`department`·`mbti`·`bio` 추가. `contactMethod=PHONE`일 때만 휴대폰 번호 형식 검증(`SignupService.validateContact`), `INSTAGRAM`은 형식 제약 없음
- `common/ContactMethod` enum 신규 추가 (`Gender`와 동일 패턴)
- `V7__add_signup_profile_fields.sql` 작성 — signup에 6개 컬럼 추가
- **(정정, 같은 날)** 공유해주신 피그마가 사전신청 화면 **전체**이고 "이 화면 안에서 뭘 필수/선택으로 할지는 아직 안 정해졌다"는 사용자 확인을 받음. 처음엔 6개 필드를 전부 필수로 구현했었는데, 이 확인 후 **전부 nullable로 되돌림** — DB 컬럼 `NOT NULL` 제거, DTO `@NotBlank`/`@NotNull` 제거(형식 검증(`@Size`/`@Pattern`/전화번호 정규식)은 값이 있을 때만 동작), 서비스에 빈 문자열→`null` 정규화(`SignupService.blankToNull`) 추가
- `docs/api-spec.md` §5, `api.md` §4, `docs/backend-requirements.md` FR-SU 섹션 갱신 (필수 표시를 전부 "⚠️ 미정"으로)
- `SignupServiceTest`·`SignupControllerTest`의 기존 생성자 호출부 전부 갱신 + `PHONE` 형식 오류·`INSTAGRAM` 허용·6개 필드 전부 `null`이어도 생성되는 케이스 테스트 추가. `./gradlew test --tests "com.darkness.wks.signup.*"` 통과 확인

**건드린 파일/패키지**
- `signup/entity/Signup.java`, `signup/dto/CreateSignupRequest.java`, `signup/SignupService.java`, `signup/SignupController.java`
- `common/ContactMethod.java` (신규)
- `db/migration/V7__add_signup_profile_fields.sql` (신규)
- `signup/SignupServiceTest.java`, `signup/SignupControllerTest.java`
- `docs/api-spec.md`, `api.md`, `docs/backend-requirements.md`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- **6개 신규 필드(`name`/`contactMethod`/`contactValue`/`department`/`mbti`/`bio`) 전부 현재 선택값(nullable)이다.** 필수/선택은 기획 미확정 — 확정되면 V7 후속 마이그레이션으로 `NOT NULL` 추가 + DTO에 `@NotBlank`/`@NotNull` 추가 필요. **이 상태로 배포하면 누구든 이름·연락처 없이 신청 가능** — 이대로 MVP 낼지 미리 확인
- **사진 업로드는 이번 릴리즈에 안 넣었다** (사용자 결정, 2026-09-15). S3 등 스토리지 연동 필요해서 리스크가 커서 2차로 보류. 이미지 저장 방식(S3 vs EC2 로컬 디스크 vs DB)은 아직 미정 — 2차 착수 전 결정 필요. 프론트에 사진 필드는 UI만 두거나 비활성화하라고 전달 필요 (FR-SU-16)
- **`gender`/`preferGender`는 그대로 유지했다.** 공유받은 피그마가 사전신청 화면 전체인 게 확인됐는데, 그 화면엔 성별 입력이 안 보인다. 즉 **현재 API는 화면에 없는 필드를 필수로 요구 중** — 프론트가 이 두 필드를 어디서/어떻게 보낼지 확인 필요. 매칭(FR-CP 궁합) 로직이 성별에 의존해서 임의로 빼지 않았음
- **`나이`는 이번 API에 안 넣었다.** 9/13 기록(FR-10)엔 나이도 Must였는데 공유된 피그마 화면엔 없어서 뺐다. 프론트가 다른 화면에서 받는지 확인 필요
- MBTI는 정규식(`^[EI][SN][TF][JP]$`, 빈 문자열은 허용)만 검증, 자기소개 글자수 상한 500자는 **피그마에 실제 숫자가 없어서 임의로 잡은 값** — 기획 확정되면 `CreateSignupRequest.bio`의 `@Size(max=...)`와 V7 컬럼 길이 같이 조정

**막힌 것 / 넘기는 것**
- 프론트·기획 확인 필요: (1) 6개 필드 중 필수는 어느 것인지, (2) 사진 저장 방식(S3/로컬디스크/기타) 및 2차 착수 시점, (3) 성별/선호성별 입력 위치, (4) 나이 필드 포함 여부·자기소개 최대 글자수

**문서 변경**
- `docs/api-spec.md`, `api.md`, `docs/backend-requirements.md`, `docs/handoff.md`

**프론트에 알려야 할 것**
- `POST /api/signups`에 `name`·`contactMethod`·`contactValue`·`department`·`mbti`·`bio` 6개 필드 추가됨. **전부 현재는 선택값** — 필수 여부 확정되면 재공지 예정. `api.md` §4 갱신함
- 사진 업로드는 이번 API에 없음 — 2차 릴리즈 예정
- 성별/나이 관련 위 미결정 항목 답변 필요

### 2026-09-14 (월) · 곽도윤 · 프론트 공유용 api.md 작성 · Claude Code

**한 일**
- 루트에 `api.md` 신규 작성 — 프론트 팀 전달 목적. 구현된 8개 엔드포인트(health/results×2/shares×2/compatibility/signups×3) 전부 실제 DTO 코드 대조해서 정리
- Base URL 섹션에 운영 도메인(`https://api.threadoffate.site`)·로컬(`localhost:8080`) 둘 다 명시

**건드린 파일/패키지**
- `api.md` (신규, 루트)

**다음 사람이 알아야 할 것**
- `docs/api-spec.md`가 여전히 내부 원본이고 `api.md`는 그걸 프론트 공유용으로 옮겨 적은 것. **스펙 바뀌면 `docs/api-spec.md` 먼저 고치고 `api.md`도 같이 갱신할 것** — 안 그러면 두 문서가 어긋남
- `api.md`에 이메일 도메인 화이트리스트가 "현재 검증 건너뛰는 중"이라고 명시해둠 — 팀 결정되면 이 문구도 지울 것

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `api.md` 신규 작성

**프론트에 알려야 할 것**
- `api.md` 파일로 전체 API 명세 전달함 (기존 `docs/api-spec.md` 미전달 상태였던 것 참고)

### 2026-09-14 (월) · 곽도윤 · CI/CD 배포 실패 원인 조사 · Claude Code

**한 일**
- EC2 배포 시 `appleboy/scp-action`이 간헐적으로 `Process exited with status 1`로 실패하는 문제 조사
- **원인: EC2 루트 파티션(`/`, 6.7G) 디스크 풀.** `df -h` 확인 결과 `Use% 100%`, 여유 0
- 근본 원인: `deploy.yml` 마지막 줄이 `docker image prune -f`(태그 없는 이미지만 정리)였는데, 매 배포가 `github.sha`로 유니크 태그를 붙여 pull하기 때문에 이전 커밋 이미지들이 태그가 남아 절대 안 지워지고 계속 쌓임
- `nginx/default.conf`가 디렉터리로 잘못 생성됐을 가능성도 확인했으나 아니었음 (정상 파일, `ubuntu` 소유)

**건드린 파일/패키지**
- `.github/workflows/deploy.yml` — `docker image prune -f` → `docker image prune -a -f`, `concurrency` 그룹 추가(동시 배포 방지, 원인은 아니었지만 안전장치로 유지), scp 스텝 `debug: true` 추가

**다음 사람이 알아야 할 것**
- **CI에서 디스크 정리는 고쳤지만, 지금 당장 EC2가 꽉 찬 상태는 수동으로 풀어야 동작한다.** SSH로 들어가서:
  ```
  docker image prune -a -f
  sudo journalctl --vacuum-size=200M
  ```
  (`--volumes`는 절대 쓰지 말 것 — postgres 데이터·certbot 인증서가 named volume에 있어서 같이 삭제됨)
- 루트 파티션이 6.7G로 상당히 작다. 이미지가 수백MB 단위라 몇 번만 더 쌓여도 다시 찰 수 있음 — 볼륨 확장이나 별도 데이터 디스크 분리를 팀에서 검토할 것

**막힌 것 / 넘기는 것**
- EC2 디스크 수동 정리는 아직 실행 안 함 (서버 접근 권한이 있는 사람이 위 명령 실행 필요)

**문서 변경**
- 없음

**프론트에 알려야 할 것**
- 없음

### 2026-09-14 (월) · 곽도윤 · signup/ 사전등록·이메일 인증 (§5) · Claude Code

**한 일**
- `POST /api/signups`, `POST /api/signups/resend`, `GET /api/signups/verify` 3개 엔드포인트 구현 (기존엔 TODO 스텁만 있었음)
- `EmailVerificationService.sendVerificationEmail()` 구현 (기존엔 `UnsupportedOperationException`)
- `SignupController` 매핑을 `/api/signup` → `/api/signups` 로 수정 (api-spec §7 복수형 규칙 위반이었음)
- `Signup` 엔티티에 `issueCoupon()`, `markVerified()`, `isVerified()` 추가 (Setter 금지 규칙 준수)
- `EmailVerificationService.verify()` 반환 타입을 `void` → `EmailVerification` 으로 변경 (호출부에서 `signup.markVerified()` 하기 위함)
- `GlobalExceptionHandler` 에 `MissingServletRequestParameterException` 핸들러 추가 (400 `INVALID_INPUT`). `GET /verify?token=` 처럼 필수 쿼리 파라미터가 없으면 기존엔 500으로 새던 걸 공통으로 막음 — signup 외 다른 패키지에도 적용되는 공통 인프라 변경이라 공유함
- Swagger 문서화: signup 3개 엔드포인트에 `@Operation`/`@ApiResponses`/`@Schema` 전부 붙임 (다른 컨트롤러와 동일 수준). `HealthController` 에 빠져있던 `@Tag` 도 추가함 (전 컨트롤러 통일)
- 로컬에서 실제 기동해 `/v3/api-docs` 확인함 — 8개 경로(health/results×2/shares×2/signups×3) 전부 정상 노출, 요청·응답 스키마와 에러 예시까지 반영됨 확인. 기존에 8080에서 돌고 있던 다른 프로세스(PID 11544, 9/11부터 떠있던 것으로 보임)는 건드리지 않고 8090으로 별도 확인 후 종료함

**건드린 파일/패키지**
- `signup/` 전체 (Controller, Service, EmailVerificationService, Repository, Entity, dto/)
- `application.yml`, `.env.prod.example`, `docker-compose.prod.yml` — `BACKEND_BASE_URL` 신규 추가 (인증 메일 링크가 백엔드 `/api/signups/verify` 를 가리켜야 해서 필요)
- `common/exception/GlobalExceptionHandler.java`, `common/HealthController.java` — 공통 인프라 소폭 수정 (위 참고)

**다음 사람이 알아야 할 것**
- **이메일 도메인 화이트리스트 미결정 상태를 그대로 반영**: `SIGNUP_ALLOWED_EMAIL_DOMAINS` 가 비어있으면(현재 로컬/prod 기본값) 도메인 검증을 **건너뛴다** (전체 허용). 학교 도메인이 정해지면 그 값만 채우면 동작함 — 코드 변경 불필요
- **메일 제목/본문은 임시 문구.** `api-spec.md` 의 `destiny.title` 처럼 기획(영채) 확정 전까지 자리만 채움 (`EmailVerificationService.SUBJECT`, 본문 텍스트 블록)
- **`resultId` 가 존재하지 않을 때 정책을 `RESULT_NOT_FOUND` 404 로 임시 결정함.** `backend-requirements.md` §10 인수조건에 "Day 3까지 결정" 이라고 되어 있던 항목 — 팀 확정되면 바꿀 것
- 재발송(`/resend`)의 "이미 인증됨" 케이스는 전용 에러코드가 없어 `INVALID_INPUT` 400 재사용함. api-spec에 명시된 에러코드 표에 없는 케이스라 전용 코드가 필요하면 팀 논의 후 추가
- 쿠폰은 이메일 1개당 신청 1건(unique 제약)이라 신청 성공 시 항상 `couponIssued: true` 로 처리함 (별도 쿠폰 테이블 없음, FR-SU-10 "1인 1회"는 이메일 unique 제약으로 자연히 보장)

**막힌 것 / 넘기는 것**
- SMTP 발송 계정 미확보 상태라 로컬에서 실제 메일 발송 확인은 못 했음 (todo.md 대로 본인 계정으로 임시 테스트 필요)
- 학교 웹메일 도메인 화이트리스트, 축제 D-day 등은 여전히 미정 (todo.md §3 그대로)

**문서 변경**
- `api-spec.md` §5 `POST /api/signups/resend` 에 Response 200 예시 추가 (기존엔 없었음). 에러코드도 `INVALID_INPUT` 재사용임을 명시

**프론트에 알려야 할 것**
- `resend` 응답 바디 형식: `{ "success": true, "data": { "mailSent": true, "message": "..." } }` (api-spec.md §5 에 추가함, 기존엔 예시 없었음)

### 2026-09-17 (목) · 차은호 · 배포 설정에서 Gemini 키 평문 제거 (#72) · Claude Code

**한 일**
- `application-prod.yml` 의 `gemini.api-key` 가 평문으로 커밋돼 있었다. `${GOOGLE_API_KEY}` 로 되돌림. `docker-compose.prod.yml` 이 이미 그 환경변수를 주입하고 있어 서버 동작에는 영향 없음
- 유입 경로: `74f202c`(2026-09-13, 배포/CI-CD 세팅)에서 줄이 처음 들어왔고, `6df9cf0`(2026-09-17, #66 작업)에서 `git add -A` 로 값이 교체돼 함께 담겼다

**건드린 파일/패키지**
- `src/main/resources/application-prod.yml` 1줄 — 곽도윤 확인 필요

**다음 사람이 알아야 할 것**
- **키 값 자체는 아직 살아 있다.** 폐기·재발급이 필요하다. 저장소 접근 권한이 있는 사람 전원이 값을 볼 수 있었다
- **과거 커밋에 값이 남아 있다.** 최신 커밋에서 지워도 `git log -p` 로 읽힌다. `git filter-repo` + force push 로 정리해야 하고, 그러면 팀 전원이 재클론해야 한다
- **저장소 public 전환은 위 둘을 끝낸 뒤에만.** 공개 즉시 크롤링 대상이 된다
- 앞으로 커밋할 때 `git add -A` 대신 변경 파일을 지정한다. 이번 사고의 직접 원인

**막힌 것 / 넘기는 것**
- 곽도윤: 키 폐기·재발급, 서버 `.env` 의 `GOOGLE_API_KEY` 확인, 히스토리 정리 시점 조율

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-17 (목) · 차은호 · saju/ 해설의 '많은 기운' 을 글자 개수 기준으로 (#70) · Claude Code

**한 일**
- 화면(`elements`)은 글자 개수인데 해설 프롬프트는 자리 가중치(`Element.strengths`) 1위를 "강한 기운"으로 말해, 독자에게 "수 3개인데 왜 화 얘기?" 로 보였음
- `buildPrompt`: 많은 기운 = 개수 최다(동점이면 전부 나열), 없는 기운 = 개수 0. 항목 이름도 "강한/약한" → "많은/없는" 으로 바꿔 개수 뉘앙스에 맞춤
- 점수·행운 장소의 가중치 계산은 그대로. 응답에 안 나오므로 화면과 충돌하지 않는다

**측정 (2001~2007년생 고유 팔자 30,672개)**
| 항목 | 값 |
|---|---|
| 개수 1위와 가중치 1위 불일치 | 19.0% |
| 그중 개수 차이 2 이상 | 0.0% |
| 개수 0 인 기운이 "강한 기운" 으로 언급됨 | 0.0% |

가중치 최대 자리(월간 1.3·월지 1.105)를 다 차지해도 2배 개수를 못 넘어서, 어긋남은 항상 개수 차이 1 에서만 생긴다.

**건드린 파일/패키지**
- `saju/ReadingGenerator.java`, `resources/prompts/reading-system.txt`, `ReadingGeneratorTest`, `docs/api-spec.md`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 개수 기준이라 동점이 잦다. "많은 기운: 나무, 불, 물" 처럼 여럿이 나열될 수 있고 그대로 자연스럽다
- 실호출 2회 확인: 개수 1위(물)를 성격 근거로 사용, 합쇼체 0

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2 `elements` 설명, `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (스키마 동일). 화면의 오행 개수와 해설 문장이 이제 같은 기준

### 2026-09-17 (목) · 차은호 · result/ 닉네임 변경 API (#68) · Claude Code

**한 일**
- `PATCH /api/results/{resultId}` 로 닉네임만 수정. 검증은 생성과 동일(필수·8자)
- 닉네임이 `compatibility` 에 복사돼 있지 않고 조회 시 `result` 에서 읽는 구조라, 공유 페이지와 친구 궁합 목록까지 자동 반영. 별도 갱신 로직 불필요
- `Result.rename()` 도메인 메서드 추가 (엔티티에 setter 없음)
- 로컬 실검증: A·B 궁합을 맺은 뒤 A 닉네임 변경 → B 의 궁합 목록·공유 페이지에 새 닉네임, `shareId`·등급·궁합 수 유지. 9자 400, 없는 ID 404

**건드린 파일/패키지**
- `result/ResultController.java`, `result/ResultService.java`, `result/entity/Result.java`, `result/dto/UpdateNicknameRequest.java`(신규) — 최선우 리뷰
- `ResultServiceTest` 2건, `docs/api-spec.md` §3, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- **인증이 없다.** `resultId` 를 아는 사람이 바꿀 수 있다. 공유해서 궁합을 맺은 뒤 욕설로 바꾸면 상대 목록에 그대로 보인다. 변경 횟수 제한(결과당 3회)·닉네임 금칙어 필터는 넣지 않았다. 필요하면 별건
- 닉네임은 행운 아이템 해시 키(생년월일·시간·성별)와 무관하고 궁합 점수(팔자만)에도 안 쓰이므로 변경해도 결과값이 흔들리지 않는다

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §3 (새 엔드포인트), `docs/handoff.md`

**프론트에 알려야 할 것**
- `PATCH /api/results/{resultId}` 추가. 닉네임 오타 수정에 재생성 대신 이걸 쓰면 공유 링크·궁합이 유지된다

### 2026-09-17 (목) · 차은호 · result/ 입력값 조회 API (#66) · Claude Code

**한 일**
- `GET /api/results/{resultId}/input`: 결과를 만들 때 입력한 값을 그대로 반환. 재입력 폼 자동 채움용. 필드 구성은 `POST /api/results` 요청과 동일
- **V9**: `result` 에 `calendar_type`·`birth_date_input`·`is_leap_month` 추가. 기존 `birth_date` 는 양력 변환값이라 음력 입력을 복원할 수 없었다. 기존 행은 SOLAR + 양력 문자열로 채움
- `birthTime` 은 `@JsonFormat(pattern = "HH:mm")`. 기본 직렬화가 `14:30:00` 이라 요청 형식과 어긋났다
- 로컬 실검증: 음력 입력(2002-03-14 윤달 아님, 양력 4/26 변환) → `/input` 이 음력 원본 그대로 반환. 시간 모름은 `null`, 없는 ID 는 404

**건드린 파일/패키지**
- `result/ResultController.java`, `result/ResultService.java`, `result/entity/Result.java`, `result/dto/ResultInputResponse.java`(신규) — 최선우 리뷰
- `db/migration/V9__add_result_input_columns.sql`, `ResultServiceTest`, 문서 3개

**다음 사람이 알아야 할 것**
- **이 응답에는 생년월일·성별이 들어 있다.** `resultId` 는 본인만 아는 값이라는 전제이므로 프론트가 URL·화면에 노출하면 개인정보가 샌다. 공유는 `shareId`
- `Result` 생성자가 둘. 9-arg 는 SOLAR 기본값으로 위임하고, 입력 원본을 저장하려면 12-arg 를 쓴다
- V9 이전에 만들어진 결과는 음력 입력이어도 양력으로 표시된다 (원본이 남아 있지 않음)

**막힌 것 / 넘기는 것**
- 최선우: 궁합 점수로 상대 팔자·생년월일시를 역산할 수 있다. 별건으로 정리해 전달 예정

**문서 변경**
- `docs/api-spec.md` §3 (새 엔드포인트), `docs/architecture.md` result DDL, `docs/handoff.md` Flyway 표

**프론트에 알려야 할 것**
- `GET /api/results/{resultId}/input` 추가. 폼 자동 채움에 그대로 사용. `resultId` 는 URL 에 노출 금지

### 2026-09-16 (수) · 차은호 · saju/ Gemini 호출 총량 상한 (#64) · Claude Code

**한 일**
- `saju/CallBudget`: 분당·일일 호출 카운터. 상한이면 Gemini 호출 없이 `LLM_UNAVAILABLE`. `ReadingGenerator` 재시도 루프에서 시도마다 확인
- 설정 `gemini.max-per-minute`(60, env `GEMINI_MAX_PER_MINUTE`)·`gemini.max-per-day`(1600, env `GEMINI_MAX_PER_DAY`). 실측(90 RPM·2,000 RPD 이상 통과)의 안전값
- 일일 창은 태평양 자정 기준 (`America/Los_Angeles` 날짜). Google 무료 한도 리셋과 동일
- 로컬 실검증: 분당 1로 띄워 새 입력 2건 → 2번째 503, 같은 입력 재사용(#62)은 카운트 안 하고 통과

**건드린 파일/패키지**
- `saju/CallBudget.java`(신규), `saju/ReadingGenerator.java`, `application.yml`, `CallBudgetTest`(신규), `docs/api-spec.md` 에러표, `docs/architecture.md`

**다음 사람이 알아야 할 것**
- 무료 한도는 **프로젝트 단위**. 로컬 테스트 키가 운영과 같은 프로젝트면 한도 공유. 축제 당일 로컬 스모크 금지
- 정확한 RPD·RPM 은 https://aistudio.google.com/rate-limit 에서 확인 후 env 로 조정. 유료 전환하면 상한 크게 올릴 것
- `ReadingGenerator` 생성자가 둘(스프링용 4-arg `@Autowired`, 테스트용 2-arg)

**막힌 것 / 넘기는 것**
- 곽도윤: nginx `limit_req` — `POST /api/results` IP당 분당 30·burst 60 (NAT 고려해 넓게). 폭주만 차단, 총량은 앱이 지킴
- 곽도윤: 유료 전환 시 Google 콘솔 일일 예산 상한

**문서 변경**
- `docs/api-spec.md` 에러 코드표, `docs/architecture.md` §Gemini, `docs/handoff.md`

**프론트에 알려야 할 것**
- `LLM_UNAVAILABLE`(503) 에 "잠시 후 다시 시도" 안내 UI 필요. 총량 상한 걸리면 1분 뒤 풀림

### 2026-09-16 (수) · 차은호 · result/ 같은 입력 해석 재사용 (#62) · Claude Code

**한 일**
- `createResult`: 같은 생년월일·시간·성별 + 같은 버전의 `reading` 이 있으면 팔자·점수·문장을 복사. Gemini 호출 없음. `resultId`·`shareId`·닉네임·행운 아이템은 새로
- `reading.version` (V8): `ReadingGenerator.promptVersion()`(시스템 프롬프트+모델 해시) × 31 + `ReadingScorer.VERSION`(손으로 올리는 상수). 프롬프트 고치면 자동으로 재사용 끊김, 점수 로직 고치면 `ReadingScorer.VERSION` 올릴 것. 기존 행은 0 이라 재사용 안 됨
- `ResultAnalysisPort.analysisVersion()` 추가. `ReadingRepository.findReusable` 은 날짜·성별·버전으로 후보를 가져와 시간은 자바에서 비교
- 로컬 실검증: 같은 입력 2회 → 2번째 Gemini 호출 없음·문장 동일. 시간 모름/입력, 성별·시간 다르면 각각 새 호출

**건드린 파일/패키지**
- `result/ResultService.java`, `result/ReadingRepository.java`, `result/entity/Reading.java`, `result/ResultAnalysisPort.java`, `result/SajuResultAnalysisAdapter.java` — 최선우 리뷰
- `saju/ReadingGenerator.java`(`promptVersion`), `saju/ReadingScorer.java`(`VERSION`), `db/migration/V8__add_reading_version.sql`, `ResultServiceTest`, 문서 3개

**다음 사람이 알아야 할 것**
- **`LocalTime` 을 JPQL 파라미터로 비교하면 안 맞는다.** `Result.birthTime` 은 `@JdbcTypeCode(SqlTypes.LOCAL_TIME)` 인데 쿼리 파라미터는 그 매핑을 안 타서 14:30 이 매치 안 됨. 그래서 시간은 자바에서 거른다. 다른 곳에서 `birthTime` 으로 조회할 일 있으면 같은 함정
- 같은 사주인 두 사람은 문장까지 같다. 2,000명 규모에서 약 5% (몰라요 비율 50% 가정). 기획이 감수하기로 함
- Postgres 는 JPQL `(:p is null and col is null)` 을 "could not determine data type of parameter" 로 거부. 쿼리 분리하거나 자바에서 처리

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2, `docs/architecture.md` DDL·흐름, `docs/handoff.md` Flyway 표

**프론트에 알려야 할 것**
- 없음 (응답 스키마 동일). 같은 입력 재생성 시 문장이 같아지는 건 의도

### 2026-09-16 (수) · 차은호 · Gemini 무료 티어 부하 실측 (#60, Day 6 항목) · Claude Code

**한 일**
- `gemini-3.5-flash-lite` 무료 키로 실제 프롬프트 크기(약 2,100토큰/건) 요청을 분당 30·60·90건씩 1분간 전송

| RPM | 전송 | 성공 | 429 | p50 | p95 | 토큰/분 |
|---|---|---|---|---|---|---|
| 30 | 30 | 30 | 0 | 4.5s | 5.5s | 67,894 |
| 60 | 60 | 60 | 0 | 4.6s | 5.8s | 135,212 |
| 90 | 90 | 90 | 0 | 4.7s | 5.3s | 204,446 |

- 90 RPM·분당 20만 토큰까지 한도 없음. 지연은 부하와 무관하게 4.5~5.8초. 그 이상은 미측정
- "막혀 있는 것"의 Gemini 실측 행 제거

**건드린 파일/패키지**
- `docs/handoff.md`, `docs/architecture.md` (Gemini 한도 문구)

**다음 사람이 알아야 할 것**
- 축제 피크가 분당 90건을 넘길 것 같으면 그 구간만 다시 재면 됨. 스크립트는 세션 스크래치라 저장 안 함. 실제 크기 요청 순차 전송 + 429 집계면 충분
- 운영 키와 로컬 키가 같으면 일일 한도를 공유함. 축제 당일엔 로컬 테스트 금지

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`, `docs/architecture.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 차은호 · saju/ 운명 제목 기획 문구 반영 (#52) · Claude Code

**한 일**
- `destiny-titles.txt` 8개를 기획(영채) 피드백 1차 문구로 교체. 임시값 제거
- 매핑(연애·결혼·자녀): 상상상 복이 겹친 운명 / 상상하 오래 사랑할 운명 / 상하상 사랑을 남길 운명 / 상하하 사랑이 깊은 운명 / 하상상 가정을 이룰 운명 / 하상하 끝내 닿는 운명 / 하하상 복을 잇는 운명 / 하하하 제 길을 갈 운명

**건드린 파일/패키지**
- `resources/destiny-titles.txt`, `DestinyTitleTest`, `docs/api-spec.md` 예시, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- "피드백 1차"라 2차 오면 파일만 다시 교체. 코드 변경 없음
- 화면이 "당신의 운명은" + 제목이라 "…운명"이 겹침("당신의 운명은 사랑이 깊은 운명"). 기획에 전달함

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2 예시, `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (스키마 동일)

### 2026-09-15 (화) · 차은호 · saju/ 프롬프트 오행 역할 설명 (#50) · Claude Code

**한 일**
- #49 이후 오행 사실은 맞지만 카드가 "흙인 당신 → 결혼은 물과 불 → 자녀는 나무"로 읽혀 독자에게 불일치로 보임. "라벨 그대로 읊기 금지"가 역할 언급까지 지운 것
- 규칙 교체: 나의 기운이 아닌 기운은 "흙의 기운인 당신에게 물의 기운은 배우자 인연을 뜻해요"처럼 뜻을 꼭 밝힌다. 각 항목 첫 문장은 나의 기운에서 시작. 연애운 기운 지정(나의 기운 + 배우자 기운) 추가
- 실호출 3회(카드와 같은 흙 일간·남성 구성): 연애·결혼·자녀 첫 문장 전부 "흙의 기운인 당신에게 X의 기운은 …을 뜻해요". 합쇼체 0. 입력 1,474토큰(+130)

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 없음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 차은호 · saju/ 프롬프트에 오행 사실 전달 (#48) · Claude Code

**한 일**
- 같은 입력인데 시도마다 언급 오행이 달랐음(첫 시도 "물과 불", 두 번째 "물과 흙"). 프롬프트에 팔자 글자만 있어 LLM이 오행을 스스로 골랐기 때문
- `ReadingGenerator.buildPrompt`: 나의 기운(일간), 강한 기운(세력 상위 2), 약한 기운(총량 10% 미만), 배우자 기운(성별 기준 배우자성)·배우자 자리의 기운(일지), 자녀 기운(자녀성)을 나무·불·흙·쇠·물로 전달
- 시스템 프롬프트: 기운 이야기는 전달된 오행 정보만 근거. 항목별로 어느 기운에서 끌어낼지 지정. 라벨("배우자 기운" 등) 그대로 읊기 금지. 합쇼체 금지 명시
- 스모크 5회(3.5-flash-lite): 결혼은 항상 나무+불, 자녀는 항상 불, 성격은 쇠+나무·물. 토큰 약 2,000 (+50)

**건드린 파일/패키지**
- `saju/ReadingGenerator.java`, `resources/prompts/reading-system.txt`, `ReadingGeneratorTest`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 합쇼체("~합니다")가 2회 중 1회꼴로 섞임. 프롬프트 금지 문구로는 완전히 안 잡힘. 거슬리면 temperature 0.9 → 0.7 검토
- 프롬프트가 등급 외 사실을 더 받으므로 `ReadingGeneratorTest` 의 기대 문자열이 오행 계산(`Element.strengths`)에 묶임. 가중치 바꾸면 테스트 문자열도 갱신

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 차은호 · saju/ 프롬프트 '등급' 표현 제거 (#46) · Claude Code

**한 일**
- 해석 문장에 "…만드는 등급입니다"가 나옴. 출력 지시 3곳이 "이 등급이 나온 기운"이라 LLM이 따라 씀
- 원칙에 "'등급'이라는 말과 등급 기호(SS, A+ 등)를 문장에 쓰지 않는다. '~하는 사주예요', '~하는 흐름이에요'처럼 풀어 쓴다" 추가. 출력 지시는 "이 운의 바탕이 되는 기운"으로
- 스모크(3.5-flash-lite) 1회: '등급' 0회, 등급 기호 0회. 5.5초, 1,953토큰

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 없음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 차은호 · result/ 행운 아이템 해시 키 (#40) + 장소 매일 변경 (#42) · Claude Code

**한 일**
- `ResultResponse.from`: 아이템 해시 키를 `resultId` → `생년월일/시간/성별`. 같은 입력을 다시 생성해도 같은 날엔 같은 아이템. 날짜 바뀌면 변경(기존과 동일)
- `LuckyPlace.of(pillars, today)`: 장소도 매일 변경. 보완 오행은 원국 기준 그대로 고정, 그 오행 풀(3~4곳) 안에서 `팔자 + 날짜` 해시. #34 의 "사람마다 고정"은 폐기
- 아이템·장소 선택 해시를 `LuckyPool.pick` 으로 통일. `String.hashCode()` 는 날짜 끝자리에 선형이라 매일 인덱스가 +1 되어 풀을 목록 순서대로 도는 주기(장소 4일·아이템 10일)가 있었음. `SplittableRandom(seed)` 로 섞어 제거. 결정적(같은 키 = 같은 결과), JVM 무관

**건드린 파일/패키지**
- `result/dto/ResultResponse.java`(3줄, 최선우 리뷰), `saju/LuckyPlace.java`, `saju/DailyLucky.java` 주석, `LuckyPlaceTest`, `docs/api-spec.md`, `docs/architecture.md`

**다음 사람이 알아야 할 것**
- 같은 생년월일·시간·성별인 두 사람은 같은 날 같은 아이템을 받는다 (의도)

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2, `docs/architecture.md` DDL 주석, `docs/handoff.md`

**프론트에 알려야 할 것**
- `luckyPlace` 도 매일 바뀐다 (#34 공지 번복). 오늘 결과와 내일 결과가 다를 수 있음

### 2026-09-15 (화) · 차은호 · saju/ + result/ 성별 반영 (#36) · Claude Code

**한 일**
- `ReadingScorer.score(pillars, gender)`: 배우자성·자녀성을 성별로 결정. 남자 재성=배우자·관성=자녀, 여자 관성=배우자·식상=자녀. 일지 보너스는 배우자성 30, 재·관 중 나머지 18. 시주 자녀 보너스는 자녀성과의 생극 관계로 통일
- `ReadingGenerator.generate(pillars, grades, gender)`: 프롬프트 첫 줄에 성별. 시스템 프롬프트에 "역할·호칭은 성별에 맞춘다, 어긋나는 표현 금지" 규칙 추가
- `ResultAnalysisPort.analyze(date, time, gender)` 시그니처 변경, `ResultService`·`SajuResultAnalysisAdapter` 호출부 반영 — 최선우 리뷰
- 보정 상수 재측정 (시주 포함 고유 팔자 265,004개): 결혼 63.0/0.93, 자녀 51.9/0.78, 연애 54.1/0.95. 남녀 원점수 중앙값 차이 0.5 이내라 공통 상수
- 시주 모름이면 자녀 시주 보너스를 0 대신 모집단 평균 13.2로 채움. 이전엔 시간 미입력자 자녀 상 비율 24%(입력자 52%)로 한 등급 손해 보던 것 → 51%로 복귀

**건드린 파일/패키지**
- `saju/ReadingScorer.java`, `saju/ReadingGenerator.java`, `resources/prompts/reading-system.txt`
- `result/ResultAnalysisPort.java`, `result/ResultService.java`(1줄), `result/SajuResultAnalysisAdapter.java`
- 테스트 5개, `docs/api-spec.md`, `docs/architecture.md`

**다음 사람이 알아야 할 것**
- 등급 분포(남녀 거의 동일): SS 6~9% / S 17~20% / A+ 25~26% / A 24~27% / B+ 18~20% / B 2~5%. 상(A+ 이상) 약 52%
- 같은 팔자라도 성별이 다르면 결혼·자녀 점수가 달라진다. 연애는 배우자성 비중만 달라져 차이 작음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2 gender 설명, `docs/architecture.md` §6, `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (요청·응답 스키마 변경 없음)

### 2026-09-14 (월) · 차은호 · saju/ + result/ 기능명세서 정렬 (#34) · Claude Code

**한 일**
- 운명 8유형 키 순서를 기능명세서와 동일하게 연애→결혼→자녀로 변경 (`destiny-titles.txt` 유형 1~8 재배열, `DestinyTitle.of`)
- 행운 아이템 오행을 명세 점수화로 교체: 사용자 궁합(십성) 40% + 오늘 일진 활성도 60%, 동점은 활성도→인성→비겁. 아이템은 `resultId + 날짜 + 오행` 해시 → 같은 팔자라도 사람마다 다름
- 행운 장소를 별도 로직으로 분리 (`LuckyPlace`): 원국 오행 세력 + 신강/신약(돕는 세력 40%/55% 컷)으로 보완 오행 → 장소. 사람마다 고정, 날짜 무관
- `Element` 에 `generates`/`controls`/`roleFor`/`strengths` 추가, `luckyAgainst` 제거. 풀 로더 `LuckyPool` 공용화
- `ResultResponse.from` 만 수정 (`DailyLucky.of(pillars, today, resultId)`, `LuckyPlace.of(pillars)`) — 최선우 리뷰

**건드린 파일/패키지**
- `saju/Element.java`, `saju/DailyLucky.java`, `saju/LuckyPlace.java`(신규), `saju/LuckyPool.java`(신규), `saju/DestinyTitle.java`, `resources/destiny-titles.txt`
- `result/dto/ResultResponse.java` (호출부 2줄)
- 테스트 4개, `docs/api-spec.md`, `docs/architecture.md`

**다음 사람이 알아야 할 것**
- 장소 오행 계산은 명세의 지장간·통근·월령 보정 생략 (기둥 가중치 0.7/1.3/1.0/0.9, 지지 0.85 만). 정밀화 요청 오면 `Element.strengths` 만 손대면 됨
- 운명 제목 문구는 여전히 임시값. 기획(영채) 8개 확정되면 `destiny-titles.txt` 만 교체

**막힌 것 / 넘기는 것**
- SS 추가 조건(FR-3) 도입 여부 → 기획 답 대기

**문서 변경**
- `docs/api-spec.md` §2 (luckyItem 매일·luckyPlace 고정, 운명 유형 순서), `docs/architecture.md` §6·DDL 주석, `docs/handoff.md`

**프론트에 알려야 할 것**
- `luckyPlace` 는 이제 사람마다 고정값 (매일 바뀌지 않음). `luckyItem` 은 매일 변경 유지

### 2026-09-14 (월) · 차은호 · saju/ 프롬프트 구체화 (#30) · Claude Code

**한 일**
- 해석 프롬프트에 원칙 추가: "기운 → 성격·행동으로 드러나는 모습 → 앞으로 흐름" 순서, 바넘 문장 금지, 근거 강도별 단정/가능성 구분, 낮은 등급은 약점 명시 후 대처법
- 항목별 필수 요소: 결혼(배우자 성향·시기 경향·결혼생활 분위기), 자녀(자녀 성향·관계 분위기·양육 주의점), 연애(끌리는 상대·반복 패턴·잘 맞는/조심할 사람)
- 스모크(3.5-flash-lite): 5.5초, 1,838토큰(이전 1,376). 문장이 "기운 좋아요 → 좋은 인연 와요" 식에서 "쇠·물 기운 → 마음 문 늦게 엶 → 이런 상대와 맞음" 식으로 구체화됨

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 출력 토큰 약 30% 증가 → 건당 비용 약 6원(유료 시), 지연 +1초. 무료 lite 한도엔 영향 없음
- 전문 역술 프롬프트의 대운·세운·건강·금전·격국·용신 항목은 범위 밖·입력 부재라 반영 안 함

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-13 (일) · 최선우 · compatibility/ 분포 보정 · Codex

**한 일**
- 실제 팔자 10만 무작위 조합을 측정해 기존 분포(귀인 0.25%·찰떡 9.94%·벗 40.11%·스침 49.70%) 확인
- 관계 계산과 순서를 유지하는 단조 구간 보정으로 목표 분포 20%·30%·30%·20% 적용
- 고정 시드 실제 팔자 분포 회귀 테스트 추가(각 목표 ±2%p)

**다음 사람이 알아야 할 것**
- 기존 DB에 저장된 궁합은 재계산하지 않으며 새로 생성하는 궁합부터 보정 점수를 사용함

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/architecture.md`, `docs/backend-requirements.md`, `docs/handoff.md`

**프론트에 알려야 할 것**
- API 구조 변경 없음

### 2026-09-13 (일) · 최선우 · compatibility/ · Codex

**한 일**
- 결과 생성 시 본인용 `resultId`와 공개 링크용 `shareId`를 함께 발급
- `GET /api/shares/{shareId}` 공개 조회와 `POST /api/shares/{shareId}/compatibility` 구현
- A의 `CompatibilityCalculator`는 호출만 하고 점수 공식은 수정하지 않음
- 신규 조합 201, 기존 정·역방향 조합 200 및 재계산 방지
- 점수 Tier 경계(90/75/61), 자기 궁합·UUID·없는 결과 예외 처리
- Result 행 잠금과 V6 무순서 유니크 인덱스로 동시·역방향 중복 방지

**건드린 파일/패키지**
- `compatibility/` Controller, Service, Repository, DTO, Tier와 테스트
- `result/` 공유 Controller·응답·Repository·ResultResponse, `db/migration/V6__add_result_share_id_and_unique_compatibility_pair.sql`

**다음 사람이 알아야 할 것**
- 친구 결과는 기존 `POST /api/results`로 만들고, 궁합 생성 후 `GET /api/shares/{shareId}`를 재조회하면 전체 친구 목록이 최신순으로 보임
- 공유 조회 응답은 내부 `resultId`와 `shareId`를 노출하지 않음
- 궁합 계산기는 A 담당 계약이므로 API 테스트에서는 mock 점수만 사용함

**막힌 것 / 넘기는 것**
- 로컬 `application-local.yml`의 DB 비밀번호 불일치로 실제 PostgreSQL 기동 검증은 못 했고 전체 단위 테스트는 통과

**문서 변경**
- `docs/api-spec.md`, `docs/architecture.md`, `docs/backend-requirements.md`, `docs/handoff.md` 갱신

**프론트에 알려야 할 것**
- path는 링크 주인의 `shareId`, body는 친구의 `guestResultId`. 신규 201, 기존 조합 200

### 2026-09-13 (일) · 차은호 · saju/ + result/ 등급 컷·운명 제목 (#17) · Claude Code

**한 일**
- 등급 컷을 기획 확정값으로: SS 94 / S 84 / A+ 74 / A 64 / B+ 52 (`Grade.of`)
- `ReadingScorer` 에 기둥 가중치(년 0.7·월 1.3·일 1.0·시 0.9, 지지 0.85) 적용 → 십성 합이 소수가 되어 점수 계단이 줄어듦 (점수 종류 결혼 57→66, 자녀 26→43, 연애 39→59). 이어서 카테고리별 선형 보정(중앙값 74, 표준편차 약 14). 자녀 식에 시주 천간 관계 보너스와 인성 감점을 추가해 재료를 늘림(같은 점수에 9% 몰리던 것이 3~4% 이하로, 100점 잘림 9.5%→5.6%). 1950~2010 고유 팔자 8,225개 기준 분포: 보정 후 양 끝은 잘라내지 않고 90~100·0~10 구간에 지수적으로 눌러 펼침(100점 몰림 0%). 결혼 SS 5%·B 5%, 자녀 SS 9%·B 2%, 연애 SS 6%·B 4%. 상/하 각 약 50/50, 운명 8조합 각 8~19%. 분포 그래프는 `~/Workspace/WKS-BE-analysis/score-distribution.html` (레포 밖)
- 운명 제목 8종을 코드 표로: `DestinyTitle.of(m, c, l)` — 결혼·자녀·연애 상(A+ 이상)/하 조합. 제목은 `resources/destiny-titles.txt` (**임시값, 영채 확정 후 교체**)
- `destinyTitle` 을 Gemini 스키마에서 제거. `reading.destiny_title` 삭제(V5). 조회 시 점수로 계산
- 결혼·자녀·연애 문장을 기획 요구대로 **8~10문장**(문장마다 `\n`)으로. 스모크: 약 450자/항목, 9.2초, 1,432토큰/건 (이전 3초·850토큰). 30초 SLA 안. 비용 약 3원/건
- 읽기 쉽게 프롬프트 조정: 짧은 문장, 사주 용어·팔자 글자 금지, 어미 다양화, 축제 행동 조언 제외(기획)
- **기본 모델을 `gemini-3.5-flash-lite` 로 변경.** 같은 무료 키로 3.6-flash 는 하루 20건인데 3.5-flash-lite 는 2,000회 연속 성공(429 없음, 약 85 RPM). 품질 스모크 4.6초·1,376토큰, 문장 수준 동등. 실제 크기 요청의 TPM 한도는 미확인 → Day 6 에 실제 크기로 재측정
- **무료 티어 한도 실측 (Day 6 항목 조기 확인)**: `429 RESOURCE_EXHAUSTED ... generate_content_free_tier_requests, limit: 20, model: gemini-3.6-flash`. 오늘 호출 약 18건 만에 막힘 → **무료 티어는 하루 20건 수준**. 축제 트래픽 불가. 결제 연결(Tier 1) 필수 — 곽도윤. 429 는 재시도 안 하도록 수정
- 테스트 54건

**건드린 파일/패키지**
- `saju/`: `Grade`, `ReadingScorer`, `DestinyTitle`(신규), `Reading`, `ReadingGenerator`, 프롬프트, `destiny-titles.txt`
- `result/`(최선우 리뷰): `ResultAnalysisPort`(`Destiny` 레코드 제거 → `destinyDescription`), 어댑터 2개, `ResultService`, `entity/Reading`, `dto/ResultResponse`, V5

**다음 사람이 알아야 할 것**
- 이제 저장되는 해석 텍스트는 운명 설명 + 결혼·자녀·연애 문장 4개뿐. 제목·등급·행운은 전부 점수·날짜에서 계산
- 등급 컷이나 제목 문구를 바꿔도 마이그레이션 없음. `Grade.of` 또는 `destiny-titles.txt` 만
- 가중치를 바꾸면 보정 상수(`calibrate` 의 중앙값·scale)도 다시 잰다. 측정 스크립트는 scratch 라 없음 — 그리드로 원점수 중앙값·표준편차 재계산하면 됨

**막힌 것 / 넘기는 것**
- 영채: 운명 제목 8개 문구
- 최선우: `result/` 변경 리뷰 (PR 은 #15 위에 쌓임)

**문서 변경**
- `docs/api-spec.md` §2 (등급 컷·제목 8종), `docs/architecture.md` §5·§6, `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (필드 구조 동일)

### 2026-09-13 (일) · 차은호 · saju/ + result/ 오늘의 행운 (#14) · Claude Code

**한 일**
- 행운 아이템·장소를 LLM 에서 빼고 코드로: `DailyLucky.of(pillars, today)`. **내 일간 vs 오늘 일진 천간의 관계(십성)** 로 행운 오행 결정(비겁→식상, 식상→재성, 재성→관성, 관성→인성, 인성→비겁) → 오행별 풀에서 (일주 번호 + 일진 번호) 로 선택. 오행은 이틀 주기(천간 오행이 둘씩 같음), 아이템·장소는 매일. 사람마다 다르고 결정적
- 풀은 `resources/lucky/items.txt`, `places.txt` (`오행=항목|항목`). **임시값. 기획이 실제 물건·실제 장소로 교체**
- `Element` enum (오행, `luckyAgainst` 관계 대응), `ReadingScorer` 오행 표 통합. #12 의 "부족 오행 채우기" 는 일진이 의미 없어 폐기
- `ReadingGenerator` 스키마·프롬프트에서 `luckyItem`·`luckyPlace` 제거 → 출력 토큰 감소
- `result/`: `reading.lucky_*` 컬럼 삭제(V4), `ResultResponse.from()` 에서 KST 오늘 기준으로 계산. `ResultAnalysisPort.AnalysisResult` 에서 lucky 제거
- 테스트 52건

**건드린 파일/패키지**
- `saju/`: `DailyLucky`(신규), `Element`(신규), `Reading`, `ReadingGenerator`, `ReadingScorer`, `SajuCalculator`(toKorean 공개), 프롬프트, 리소스 2개
- `result/`(최선우 리뷰): `ResultAnalysisPort`, `SajuResultAnalysisAdapter`, `FakeResultAnalysisAdapter`, `ResultService`, `entity/Reading`, `dto/ResultResponse`, V4

**다음 사람이 알아야 할 것**
- `POST` 응답과 다음 날 `GET` 응답의 `luckyItem`·`luckyPlace` 가 다르다. 의도된 동작 (api-spec 명시)
- 일진은 lunar-java `getDayInGanZhi()` (GMT+8 기준이지만 날짜 단위라 KST 와 동일). 오늘 날짜는 `Asia/Seoul`
- 풀 항목 수가 달라도 됨. 오행별 최소 1개 없으면 기동 시 실패
- 오하아사식 "별자리 순위" 는 채택 안 함 (공식 API 없음, 별자리 기준이라 사주와 무관)
- 관계에 음양을 더하면 십성 10개(정재일·편재일…)로 "오늘 유형" 문구를 매일 다르게 만들 수 있음. 응답 필드 추가라 프론트 합의 후

**막힌 것 / 넘기는 것**
- ~~기획: `lucky/items.txt`·`places.txt` 실제 목록~~ 9/14 확정본 반영 (오행당 아이템 10개, 장소 3~4개)
- 최선우: `result/` 변경 리뷰

**문서 변경**
- `docs/api-spec.md` §2 (lucky 의미), `docs/architecture.md` §5·§6, `docs/handoff.md`

**프론트에 알려야 할 것**
- `luckyItem`·`luckyPlace` 가 매일 바뀜. 캐시하지 말 것

### 2026-09-13 (일) · 차은호 · saju/ 행운 오행 (#12) · Claude Code

**한 일**
- `Element` enum 추가: 천간·지지 → 오행, `Element.lacking(pillars)` = 팔자에서 가장 적은 오행(용신 근사). 결정적
- 프롬프트에 "행운 오행: 토 (색: 황색·갈색)" 힌트 추가. Gemini 는 그 오행의 색·소재·장소 안에서 아이템·장소를 고름
- `ReadingScorer` 의 오행 표를 `Element` 로 통합 (점수 변화 없음, 분포 재측정 동일)
- 스모크: 행운 오행 토 → 아이템 "약과", 장소 "후문 언덕". 이전엔 3건 연속 "정각원"·청색 편향

**건드린 파일/패키지**
- `saju/Element.java`(신규), `saju/ReadingScorer.java`, `saju/ReadingGenerator.java`, `resources/prompts/reading-system.txt`, 테스트

**다음 사람이 알아야 할 것**
- 행운 오행은 "부족 오행 보충" 관행. 정통 용신(신강·신약 판단)은 유파별로 달라 채택 안 함
- 응답에 행운 오행은 안 나감. 프론트가 "수 기운이 부족해서 파란색" 같은 문구를 원하면 `ResultResponse` 에 필드 추가만 하면 됨 (`Element.lacking(pillars)`)

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-13 (일) · 차은호 · result/ 연결 초안 (#10, 최선우 리뷰용) · Claude Code

**한 일**
- `POST /api/results` 를 가짜 분석기 대신 `saju/` 에 연결하는 초안. **`result/` 는 최선우 담당이라 Draft PR 로 올리고 최선우가 리뷰·머지**
- `CreateResultRequest`: `calendarType`(필수)·`isLeapMonth` 추가, `birthDate` 를 `String`(yyyy-MM-dd) 으로, `birthRegion` 제거
- `ResultService`: `BirthDate.parse(...).toSolar()` 로 양력 변환 후 계산·저장. 없는 날짜·윤달, 1950-01-01 ~ 오늘 밖 → 400 `INVALID_INPUT`
- `SajuResultAnalysisAdapter` 신규: `SajuCalculator` → `ReadingScorer` → `Grade.of` → `ReadingGenerator`. **기본 활성**. 가짜 분석기는 `app.result.fake-analysis-enabled=true` 일 때만 (기본값 뒤집음)
- `ResultAnalysisPort.analyze(LocalDate solar, LocalTime)` — 지역 파라미터 제거
- `ResultResponse` 에 `zodiac`
- 로컬 E2E (Postgres 15 + 실제 Gemini): 양력·음력 윤달·시간 모름 201, 없는 윤달·형식 오류·1949년 400. Gemini 3.1~3.4초/건

**건드린 파일/패키지**
- `result/`: `CreateResultRequest`, `ResultService`, `ResultAnalysisPort`, `FakeResultAnalysisAdapter`, `SajuResultAnalysisAdapter`(신규), `dto/ResultResponse`, 테스트 2개

**다음 사람이 알아야 할 것**
- **해결됨**: `result.birth_time` 이 입력 14:30 대신 DB에 05:30으로 저장되던 문제는 `Result.birthTime`을 JDBC 네이티브 `LOCAL_TIME`으로 매핑해 전역 UTC 보정 대상에서 제외함
- `reading` 에 **점수(0~100)를 저장**한다 (V3, 9/13 결정). 등급은 응답 시 `Grade.of(score).label()`. 등급 컷을 바꿔도 마이그레이션 없이 코드만 고치면 됨
- LLM 실패 시 팔자를 미리 저장해 재시도 가능하게(FR-RD-07) 하는 건 안 넣음. 계산이 수 ms 라 재요청 시 재계산이 더 단순
- 옛 9/12 기록의 "`fake-analysis-enabled=false` 로 끈다" 는 반대로 바뀜: 기본이 실제, `true` 면 가짜

**막힌 것 / 넘기는 것**
- 최선우: PR 리뷰·머지

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (api-spec 은 이미 반영됨)

### 2026-09-13 (일) · 차은호 · saju/ 등급·해석 (#6) · Claude Code

**한 일**
- `ReadingCategory` 를 api-spec 기준 3종(MARRIAGE, CHILDREN, LOVE)으로 교체. 옛 5종 스텁 제거
- `Grade` 6단계 `SS S A+ A B+ B` (기획 확정). 점수→등급 경계는 `Grade.of()` 한 곳
- `ReadingScorer`: 팔자 → **0~100 점수**. 일간 기준 십성 분포·일지(배우자궁)·시지(자녀궁)·합충·도화살 가중치. 순수 함수. 등급 변환은 API 계층에서 `Grade.of(score)` (궁합의 점수→Tier 와 같은 구조)
- `ReadingGenerator`: Gemini `gemini-2.5-flash` 1회 호출, JSON 스키마 강제, thinking 0, 재시도 1회, 실패 시 `LLM_UNAVAILABLE`. 로그는 ms·토큰 수만
- 프롬프트 `resources/prompts/reading-system.txt` (보살 톤, 행운 장소는 동국대 캠퍼스 안)
- 테스트 8건: 등급 결정성·경계, 프롬프트 개인정보 미포함, 파싱 실패

**건드린 파일/패키지**
- `saju/` 만: `ReadingCategory`, `Grade`, `Reading`, `ReadingScorer`, `ReadingGenerator`, `resources/prompts/`

**다음 사람이 알아야 할 것**
- 등급 분포(1950~2010 고유 팔자 8,225개): 결혼 SS 13%·B 4%, 자녀 SS 10%·B 16%, 연애 SS 4%·B 8%. 기획이 더 후하게/짜게 원하면 `ReadingScorer` 상수만 조정
- Gemini 모델 `gemini-3.6-flash` (`gemini.model`, 기본값). **`gemini-2.5-flash` 는 신규 키에 404** ("no longer available to new users"). 3.x 는 `thinkingBudget` 400 → `thinkingLevel: MINIMAL` 사용
- 실제 호출 스모크 완료: 약 3초, 약 770 토큰/건, 응답 스키마 7필드 정상. `GeminiSmokeTest` 는 `GOOGLE_API_KEY` 있을 때만 실행(CI 제외)
- 최선우 연결 지점: `SajuCalculator.calculate()` → `ReadingScorer.score()` (점수) → `Grade.of(score)` (등급) → `ReadingGenerator.generate(pillars, grades)`. `Reading.contents()` 는 `EnumMap<ReadingCategory,String>`, 응답 `grade` 는 `Grade.label()`. 점수를 저장할지 등급만 저장할지는 `reading` 테이블 설계(최선우) 몫
- 런타임 LLM 유지 결정(9/13). 대안으로 검토한 사전 생성(카테고리 3 × 등급 6 × 일주 60 문장을 미리 만들어 리소스로, 런타임 호출 0회)은 축제 순간 트래픽·429 위험을 없애는 방법. 무료 한도 실측 후 부족하면 이 안으로 전환 가능

**막힌 것 / 넘기는 것**
- Day 6 RPM·RPD 실측 (무료 티어 한도가 축제 트래픽에 부족하면 결제 연결 — 곽도윤)

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (등급 6단계는 PR #5 에서 api-spec 반영됨)
### 2026-09-13 (일) · 차은호 · 문서 (#7, 기획 명세 반영) · Claude Code

**한 일**
- 프론트 기능명세서(Figma v0.2, 9/13)와 기획 결정을 백엔드 문서에 반영
- 궁합 Tier 구간 변경 기록: 귀인 90~100 / 찰떡 75~89 / 벗 61~74 / 스침 0~60. **코드(`CompatibilityCalculator`·`CompatibilityService`)는 최선우가 반영**

**건드린 파일/패키지**
- `docs/api-spec.md` §4, `docs/architecture.md` §7, `docs/backend-requirements.md` FR-CP-03·TR-06·인수 조건, `docs/convention.md`, `docs/handoff.md`

**다음 사람이 알아야 할 것 — 프론트 명세와 어긋나는 것 (팀 결정 필요)**
- **API 계약 불일치.** 프론트 명세는 `POST /readings`, `GET /shares/{shareId}`, `POST /shares/{shareId}/compatibility`, `GET /me/friends`, `POST /me/threads` 와 응답 타입 `Reading{id, shareId, zodiac, destiny, sections(3), cardGrades, lucky}` 를 쓰고, 원본을 "백엔드 저장소 `docs/api/openapi.yaml`" 이라고 적음. **우리 레포에 그 파일 없음.** 우리 계약은 `docs/api-spec.md` (`/api/results`, `resultId`, `destiny`, `fortunes`). 프론트 Open Question Q3 담당은 `@hairyung2002`. 9/17 전에 한쪽으로 맞춰야 연동 가능
- **요청 형식.** 프론트는 `birthDate` "숫자 8자리", 우리는 `yyyy-MM-dd`. 시진 선택 UI 인데 전송 규칙(가운데 시각, 자시 두 칸)이 프론트 문서에 없음 → api-spec §2 를 프론트에 공지해야 함
- ~~**사전신청 수집 항목.** 프론트 FR-10 은 학교 이메일·이름·연락처(인스타/전화)·사진·학과·나이·MBTI·자기소개 를 Must 로 수집. 우리 규칙은 "이름·전화번호는 받지 않는다, 컬럼도 없다". `signup/` 스키마·개인정보 정책 결정 필요 (곽도윤)~~ → **2026-09-15 해결.** 이름·연락처·학과·MBTI·자기소개 수집으로 정책 변경(FR-SU-11 개정, V7). 사진은 2차 릴리즈로 보류(FR-SU-16). 나이는 이번 API에 포함 안 함 — 프론트 확인 필요
- **범위.** 프론트 Must 에 운명의 실(`/me/threads`), 소개팅 후보 열람(`/matching`), 세션(`/me/*`)이 포함. 우리 1차 범위 "하지 않는 것"에 매칭·로그인 있음. 소개팅 페이지는 축제 당일(9/29) 오픈
- **일정.** 축제 2026-09-29 ~ 10-01, MVP 마감 9/17
- 프론트 명세에서 확인된 결정(이미 반영됨): 지역 입력 없음, 등급 B~SS 6단계, 십이간지 캐릭터, 행운 장소는 동국대 안, 닉네임 8자, 성별 남/여

**막힌 것 / 넘기는 것**
- 최선우: 궁합 Tier 코드 반영 + 경계 테스트(60/61/74/75/89/90)
- 곽도윤: 계약 불일치(Q3)·사전신청 개인정보·범위 재합의 주도

**문서 변경**
- 위 5개 파일

**프론트에 알려야 할 것**
- 없음 (계약 정렬은 별도 논의)
### 2026-09-13 (일) · 차은호 · saju/ · Claude Code

**한 일**
- 만세력 라이브러리 확정: `cn.6tail:lunar:1.7.4` (MIT, Maven Central, 런타임 의존성 0). `build.gradle` 에 추가
- `SajuCalculator` 구현: 연·월·일·시주 계산. 절기·60갑자는 lunar-java, 진태양시는 시도별 경도 보정
- `KoreanLunarCalendar` 추가: 한국 음력 → 양력 변환 (KASI 표, 1940~2030)
- 단위 테스트 20건 (`SajuCalculatorTest`, `KoreanLunarCalendarTest`). 스프링·DB 없이 실행
- 검증: KASI 기준 manseryeok 2.0.0 과 대조. 팔자 38,064건 중 절입 순간 ±30초 이내 4건 외 전부 일치, 음력→양력 22,280일 전부 일치
- `gradlew` 실행 권한 추가 (기존 644 라 `./gradlew` 실행 불가였음)

**건드린 파일/패키지**
- `saju/SajuCalculator.java`, `saju/KoreanLunarCalendar.java`, `saju/BirthDate.java`, `saju/CalendarType.java`, 테스트 3개
- `build.gradle` (의존성 1줄), `gradlew` (파일 모드), `docs/architecture.md`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- lunar-java 는 **GMT+8 벽시계 기준**. 절기 판정(연주·월주)엔 KST−1h 를 넣고, 일주·시주는 출생지 진태양시로 따로 계산한다. 이 구조를 무너뜨리면 입춘 전후 1시간 구간에서 연주가 틀린다
- lunar-java 의 **음력은 중국 기준**이라 쓰지 않는다 (1950~2010 사이 3.2% 날짜가 한국 음력과 다름). 음력은 `KoreanLunarCalendar.toSolar()` 로 먼저 양력 변환 후 `SajuCalculator` 에 넣는다
- 관법 결정: 진태양시 23시(자시 시작) 이후는 다음날 일주·시주 (포스텔러 기본값과 동일. 포스텔러 "야자시/조자시" 체크는 반대 관법). `birthTime` null 이면 정오 기준으로 연·월·일주 판정, 시주 null. `birthRegion` 이 null 이거나 표에 없으면 서울 경도
- 미보정(의도적): 균시차 ±16분, 1954~61년 UTC+8:30, 1948~60·1987~88 서머타임. 대상 연령대에 영향 없음. 필요하면 `SajuCalculator` 의 `apparent` 계산 한 줄
- 음력 입력(`calendarType`, `isLeapMonth`)은 현재 `CreateResultRequest` 에 없음. **최선우 확인 필요**: DTO 필드 추가 후 `KoreanLunarCalendar.toSolar()` 호출
- 09-13 Codex 기록의 "lunar-java 불일치"는 무보정 입력 재현이며 위 구조로 해결됨. `docs/lunar-java-validation.md`, `tools/check_lunar.py` 는 탐색용이라 커밋하지 않음

**막힌 것 / 넘기는 것**
- ~~기획 결정 대기 1건: 자시 두 칸 분리~~ 9/14 승인. 프론트: `자시 00:00~01:30` → `00:45`, `자시 23:30~24:00` → `23:45`
- 최선우: `CreateResultRequest` 에 `CalendarType calendarType`(saju 패키지 enum)·`Boolean isLeapMonth` 추가, `birthDate` 를 `String` 으로, `birthRegion` 삭제. `BirthDate.parse(...).toSolar()` 결과(양력)를 `analyze()` 와 `Result` 저장에 사용
- 최선우: 응답에 `zodiac` 추가 — `SajuPillars.zodiac()` (또는 저장된 `year_pillar` 로 `Zodiac.fromYearPillar`). 컬럼 추가 불필요
- `saju/` 출력 형태 미확정: `ReadingCategory` 5종(점수) vs `result/ResultAnalysisPort` (운명 + 결혼·자녀·연애 등급 + 행운 아이템·장소). 등급 문자열 집합·산출 기준도 미정. 이게 정해져야 `ReadingScorer`·`ReadingGenerator` 착수 가능
- TR-01 대체: 포스텔러 만세력 2.2 와 7건 대조(입춘 전후·자시·시진 경계·설날) 전부 일치, `SajuCalculatorTest.matchesPosteller` 에 고정. 실제 인물 데이터가 생기면 추가

**문서 변경**
- `docs/architecture.md`: 스택 표에 lunar-java 추가, §9 미결정에서 만세력 항목 제거, §6 에 계산 방식 반영
- `docs/api-spec.md` §2: 음력 필드 2개 추가, 시진 입력 규칙 추가 (필드 추가라 사전 합의 불필요, 프론트 공지 필요)

**프론트에 알려야 할 것** (기획 확인 후 공지, 2026-09-13 기획에 전달)
- 시간은 시진(2시간) 선택 UI 그대로. 프론트가 선택한 칸의 **가운데 시각**을 `birthTime: HH:mm` 으로 보낸다 (묘시 05:30~07:30 → `06:30`). 같은 시진이면 팔자가 같아서 문제 없음
- **자시 두 칸 분리 확정(9/14 기획)**: `자시 00:00~01:30` → `00:45`, `자시 23:30~24:00` → `23:45`. 자정을 걸쳐서 날짜+자시만으로는 새벽/밤 구분이 안 되고, 둘은 일주가 하루 다름
- **`birthRegion` 제거 확정(9/13 기획)**. 시진 단위 입력이라 지역 보정(−24~−34분)이 칸 경계에 안 닿음. api-spec 에서 뺌. `CreateResultRequest` 필드 삭제는 최선우, DB 컬럼 `birth_region` 은 그대로 두고 NULL 저장(마이그레이션 불필요). `SajuCalculator` 지역 파라미터는 `null` 로 넘기면 서울 기준
- `birthTime: null` = 시간 모름, 3주 계산
- **음력 지원 확정(9/13 기획)**. api-spec §2 에 `calendarType`·`isLeapMonth` 추가함. 변환은 `saju/BirthDate` 가 맡는다:
  `BirthDate.parse(calendarType, "yyyy-MM-dd", isLeapMonth).toSolar()` → 양력 `LocalDate`. 음력 2월 30일처럼 `LocalDate` 로 못 담는 날짜가 있어 **DTO 의 `birthDate` 는 `String` 이어야 한다**. 잘못된 날짜·윤달은 `IllegalArgumentException` → `INVALID_INPUT` 매핑 필요
- 알려진 한계: 절입 순간이 선택 칸 안에 걸리면 월주가 옆 값 (약 1/700). 분 단위 입력 없인 어느 서비스든 동일

### 2026-09-13 (일) · 차은호 · saju/ 도입 검증 · Codex

**한 일**
- `lunar-java 1.7.4` JAR와 `manseryeok 2.0.0`을 실제 실행해 합성 입력 21건 비교: 15건 일치, 6건 불일치. 불일치로 검사 종료 코드 1 반환
- 1997-02-07/08은 음력 날짜가 하루 다름. 2024 입춘·2021 한로 직전 각 2건은 한국 시각을 그대로 넣은 lunar-java의 연주·월주가 먼저 바뀜
- 원인은 중국 기준 음력 및 절기 시각(UTC+8)과 한국 기준(UTC+9)의 차이. 한로 경계는 KASI 2021 역서의 10:39 표기와도 대조
- 비교 스크립트 Python 문법 검사와 `git diff --check` 통과. 운영 코드·의존성은 변경하지 않음

**건드린 파일/패키지**
- `tools/check_lunar.py`: 외부 라이브러리 경로를 받아 실행하는 독립 비교 스크립트
- `docs/lunar-java-validation.md`: 조건·결과·출처·제한·재실행 방법
- `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 라이브러리 채택은 아직 미결정. 한국 시각을 무보정으로 전달하는 방식에서 문제가 재현된 것이며 lunar-java 전체가 잘못됐다는 의미는 아님
- 비교는 양력 입력, 진태양시 OFF, lunar-java `sect=2`와 manseryeok `splitJasi`로 자시 정책을 맞춤. 두 라이브러리의 기본 자시 정책은 서로 다름
- 15/21은 정확도 지표가 아님. 경계 사례를 의도적으로 선정했고, 비교 대상 manseryeok도 독립적인 정답으로 인증한 것은 아님
- 외부 다운로드가 DNS 제한으로 실패하여 로컬 1.7.4 JAR를 사용. 최신 버전 검증으로 보고하면 안 됨
- 재실행: `python3 tools/check_lunar.py /absolute/path/lunar.jar /absolute/path/node_modules/manseryeok` (Python 3, Java 17+, Node 및 사전 다운로드한 라이브러리 필요)
- 이번 실행의 로컬 자료는 `/tmp/claude-1000/-home-eunho-Workspace-WKS-BE/a0984da8-3955-4acb-8aef-0e1fc19c9634/scratchpad/` 아래 `lunar-java/lunar-1.7.4.jar`, `mstest/node_modules/manseryeok`. 임시 경로이므로 다른 환경에서는 별도 확보 필요
- 입력에서 일괄적으로 1시간을 빼면 일주·시주까지 달라질 수 있음. 음력 변환도 별도 문제이므로 이 방법을 해결책으로 적용하지 말 것
- `SajuCalculator`는 여전히 스텁. `manseryeok` 사용 시 Node 실행 환경이 필요하며 아직 추가하지 않음
- 앞선 대화의 5개 해석·0~100 점수 요약보다 아래 9/12 B 인계와 현재 API 문서를 우선 확인할 것. 현재 B 응답은 운명·세 운세 등급·행운 아이템·장소 구조

**막힌 것 / 넘기는 것**
- 채택할 최신 버전으로 재실행하고 1997 음력·2024 입춘 사례를 KASI 원본 데이터에 직접 대조. 현재 이 사례들은 두 구현 간 차이 재현까지 완료
- 실제 인물 5명, 음력 역변환·잘못된 윤달, 지역·균시차·과거 표준시/DST, 시간·지역 null은 미검증
- 절기 당일 시간 모름은 연주·월주까지 불확실할 수 있으므로 정책 합의 필요
- 한국 기준 보정 작업량과 Node 운영 부담을 비교해 lunar-java 또는 manseryeok 선택. 아직 도입·Java 이식·보정 구현을 진행하지 않음

**문서 변경**
- `docs/lunar-java-validation.md` 추가, `docs/handoff.md` 갱신
- AGENTS.md / api-spec / architecture / conventions 변경 없음

**프론트에 알려야 할 것**
- 없음 (이번 작업의 API 변경 없음)
### 2026-09-13 (일) · 최선우 · result/·compatibility/ · Codex

**한 일**
- `GET /api/results/{resultId}` 구현 및 Swagger 명세 추가
- Result·Reading DB 조회와 궁합 최신순 조회 연결
- `FakeResultAnalysisAdapter`를 제거하고 `SajuCalculator` → `ReadingScorer` → Gemini 해석 경로를 유일한 `ResultAnalysisPort` 구현으로 고정
- 잘못된 UUID 400, 없는 Result 404 처리
- 조회 시 A 분석 모듈 미호출 단위 테스트 및 실제 PostgreSQL 재조회 검증

**건드린 파일/패키지**
- `result/`의 Controller, Service, 응답 DTO와 테스트
- `compatibility/CompatibilityRepository`

**다음 사람이 알아야 할 것**
- POST와 GET이 `ResultResponse`를 공유하며 POST의 `compatibilities`는 빈 배열
- 궁합 조회는 origin/guest 양쪽을 한 쿼리로 조회하고 `createdAt` 내림차순 정렬

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md`에 POST 빈 궁합 배열 반영

**프론트에 알려야 할 것**
- POST 응답에도 `compatibilities: []`가 포함됨

### 2026-09-12 (토) · 최선우 · result/ · Codex

**한 일**
- `POST /api/results` 요청·응답 DTO, Bean Validation, Controller, Swagger 골격 추가
- A 담당 모듈이 구현되기 전 사용할 가짜 분석기와 교체 가능한 연동 인터페이스 추가
- 가짜 분석 결과를 Result·Reading으로 저장하고 201 응답하도록 연결
- 결과 응답을 운명·세 운세 등급·행운 아이템·장소 구조로 변경하고 V2 마이그레이션 추가
- `reading`을 Result당 1행인 1:1 구조로 변경하고 점수·카테고리 컬럼 제거

**건드린 파일/패키지**
- `result/`의 Controller, Service, DTO

**다음 사람이 알아야 할 것**
- 현재 `FakeResultAnalysisAdapter`가 고정된 운명·등급·해석을 반환함
- A API가 완성되면 B가 `ResultAnalysisPort`의 실제 어댑터를 `result/`에 추가하고 `app.result.fake-analysis-enabled=false`로 가짜 구현을 끄면 됨
- 실제 어댑터에서는 전체 운명 콘텐츠를 Gemini 한 번의 호출로 생성해야 함
- 허용할 등급 문자열 전체 목록과 등급 산출 기준은 팀 합의 필요

**막힌 것 / 넘기는 것**
- 실제 계산·점수·일괄 해석은 A 담당 구현 필요

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- `POST /api/results`의 `nickname` 최대 길이가 8자로 변경됨
- 결과 응답이 `destiny`, `fortunes`, `luckyItem`, `luckyPlace` 구조로 변경됨

### 2026-09-13 (일) · 곽도윤 · 인프라 · Claude Code

**한 일**
- 배포 파이프라인 초안 구성: `Dockerfile`(멀티스테이지 빌드), `docker-compose.prod.yml`(postgres+app+nginx), `nginx/default.conf`
- `application-prod.yml` 신설 — base `application.yml`엔 datasource가 아예 없어서(로컬 프로필에만 존재) prod로 그냥 못 띄우는 상태였음. 전부 환경변수 주입으로 처리
- `.github/workflows/deploy.yml` 추가 — `main` push 시 GHCR로 이미지 빌드/푸시 → **`docker-compose.prod.yml`/`nginx/` 를 scp로 EC2에 동기화** → SSH로 `docker compose pull && up -d` + nginx restart 자동 실행
  - (처음엔 이미지 배포만 자동화했다가, compose/nginx 파일이 바뀌면 여전히 수동 scp가 필요한 구멍이 있어서 sync 단계 추가함)
- `.env.prod.example` 추가 (실값은 EC2에서 `.env`로 채우고 git에 안 올림 — 이건 비밀값이라 앞으로도 계속 수동)

**건드린 파일/패키지**
- `Dockerfile`, `.dockerignore`, `docker-compose.prod.yml`, `nginx/default.conf`, `.env.prod.example`
- `src/main/resources/application-prod.yml` (신규)
- `.github/workflows/deploy.yml` (신규)
- `.gitignore` (`.env`, `.env.prod` 추가)

**다음 사람이 알아야 할 것**
- EC2에서 실행할 땐 `SPRING_PROFILES_ACTIVE=prod` 필수 (compose 파일에 이미 박아둠)
- GHCR 이미지가 private면 EC2에서도 `docker login ghcr.io` 필요 — 워크플로에 포함돼 있음
- nginx는 HTTP(80)만 열어둔 상태. 도메인 정해지면 `nginx/default.conf`의 `server_name` 채우고 certbot으로 443 추가할 것

**막힌 것 / 넘기는 것**
- **GitHub Secrets 미등록** → `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_APP_DIR` 를 리포지토리 Settings에 등록해야 워크플로가 실제로 돈다
- **EC2 인스턴스 자체가 아직 없음** → 인스턴스 생성, 보안그룹(80/443/22 오픈), Docker 설치, 최초 1회 `docker-compose.prod.yml`+`nginx/`+`.env` 서버에 배치 필요
- 프론트 배포 도메인 미확정 → CORS_ALLOWED_ORIGINS/nginx server_name 임시값으로 둠

**문서 변경**
- `architecture.md`의 기존 인프라 결정(Docker·EC2·nginx·GitHub Actions)은 그대로 유지, 구체 구현만 추가

**프론트에 알려야 할 것**
- 없음 (API 변경 아님)

---

### 2026-09-11 (금) · 곽도윤 · 공통 · —

**한 일**
- 멀티 AI 도구 대응 문서 구조 확립
    - 규칙 원본은 루트 `AGENTS.md` 하나. `CLAUDE.md` / `.cursor/rules/` / `.github/copilot-instructions.md` 는 포인터일 뿐
    - 패키지별 `AGENTS.md` 추가 (담당자별 규칙). 가장 가까운 파일이 우선
- 패키지 루트를 `com.darkness.wks` 로 확정
- 스택 확정: Java 17, Spring Boot 4.1.1, springdoc 3.1.1, Redis·Security·JWT 제외

**건드린 파일/패키지**
- `AGENTS.md`, `CLAUDE.md`, `.cursor/rules/project.mdc`, `.github/copilot-instructions.md`
- `docs/*`, 각 패키지 `AGENTS.md`

**다음 사람이 알아야 할 것**
- **규칙을 도구별 파일에 복붙하지 마라.** 원본은 루트 `AGENTS.md` 하나다
- **Spring Boot 3이 아니라 4다.** AI가 Boot 3 관례(springdoc 2.x, Jackson 2, deprecated API)를 쓰면 기동 실패
- 초기 세팅 때 AI가 패키지명을 임의로 바꾼 사고가 있었다. 생성 결과를 항상 검증할 것
- `resultId` 는 반드시 **UUIDv4**. 순번이면 남의 결과를 전부 긁힌다
- **점수는 코드가, 문장만 LLM이.** 이 경계를 흐리지 말 것
- LLM은 **Gemini Flash 무료 티어**(`com.google.genai:google-genai`)로 변경됐다.
  무료 티어는 프롬프트가 Google 제품 개선에 쓰일 수 있으므로
  **프롬프트에 생년월일·닉네임을 넣지 말고 팔자와 등급만 보낸다**
- 무료 티어 RPM 제한 때문에 **전체 운명 콘텐츠를 한 번의 호출로 생성**한다. 항목별 호출 금지
- 이름·전화번호는 받지 않는다. 스키마에 컬럼도 없다
- Git 전략: `main`(프로덕션) ← `dev`(통합) ← `feat/<이슈번호>-<설명>`.
  **작업은 이슈부터 파고, PR base는 `dev`.** 릴리즈 PR만 `main`

**막힌 것 / 넘기는 것**
- 만세력 라이브러리 미선정 → Day 1 최우선.
  **직접 구현 금지** — 절기 경계·진태양시·야자시로 일주일이 그냥 간다
- 축제 D-day 미확정 → 일정 역산 불가

**문서 변경**
- 전부 신규 작성 (기존 `kr.ac.dongguk.festival` 기준 문서는 폐기)

**프론트에 알려야 할 것**
- `docs/api-spec.md` 확정본을 **Day 1에 전달**해야 프론트가 목 데이터로 착수 가능
- CORS에 `localhost:3000` 허용 필요

---

## 지난 기록

(3일 지난 항목을 여기로 옮긴다)

# AGENTS.md

이 저장소의 **규칙 원본**이다. 어떤 AI 도구를 쓰든(Claude Code · Codex · Copilot · Cursor …) 이 파일을 따른다.
도구별 파일(`CLAUDE.md` 등)은 이 파일을 가리키기만 한다. **내용을 복붙하지 않는다.** 규칙이 두 곳에 있으면 곧 어긋난다.

세부 규칙은 `docs/` 에 있다. 여기에는 **어기면 사고가 나는 것**만 적는다.

---

## 프로젝트

동국대 축제(2026-09-29 ~ 10-01)용 백엔드. 사주 결과 → 친구 궁합 → 소개팅.
Java 17 · Spring Boot 4.1.1 · Gradle · PostgreSQL 16 · JPA · Flyway · Gemini(`google-genai`). 패키지 루트 `com.darkness.wks`.
프론트는 별도 레포·별도 팀이다. **우리가 프론트에 보장하는 계약은 `docs/api-spec.md` 뿐이다.**

## 작업 전에 읽는 순서

1. `docs/handoff.md` — 팀의 현재 상태, 예약된 Flyway 번호, 막힌 것. **작업이 끝나면 기록한다** (템플릿 그대로, 최신이 위)
2. `docs/plan.md` — V1 기획 원본. 기능·정책은 여기가 원본이다
3. `docs/architecture.md` — 구조·스키마·기술 선택의 원본. 코드보다 우선한다
4. `docs/convention.md` — 코드 규칙
5. 필요할 때: `docs/backend-requirements.md`(요구사항 ID·인수 조건), `docs/api-spec.md`(프론트 계약), `docs/git-workflow.md`(브랜치·PR)

**문서와 코드가 다르면 문서가 맞다. 코드를 고친다.** 문서끼리 어긋나면 임의로 고르지 말고 멈추고 묻는다.
문서에 "구현 전"이라고 적힌 것(`auth/`·`member/`·`wallet/`·`dating/`, 로그인, 소개팅)은 코드에 아직 없다. 현재 상태는 `handoff.md` 를 본다.

## 멈추고 물어야 하는 경우

- **`docs/plan.md` 의 `TBD` 항목.** 임의로 구현하지 않는다
- **새 라이브러리.** 좋은 후보가 떠올라도 추가하지 말고 팀에 묻는다 (JWT 라이브러리는 승인 대기 중)
- **남의 패키지 수정.** 아래 담당 표를 본다
- `common/`, `ErrorCode.java`, `build.gradle`, `application.yml`, `db/migration/`, 이 파일은 여러 명이 건드리는 곳이라 **수정 전에 팀 채널에 알린다**
- 프론트 계약(`api-spec.md`)의 필드 삭제·타입 변경. 추가는 자유

## 담당

| 담당 | 패키지 |
|---|---|
| 차은호 | `saju/` |
| 최선우 | `result/`, `compatibility/` |
| 곽도윤 | `signup/`, `common/`, `auth/`, `member/`, 인프라 |
| 미정 | `wallet/`, `dating/` |

## 어기면 안 되는 것

**인증·개인정보**
- 사주·궁합·공유 API 는 **로그인 없이 동작**한다. 인증은 `/api/me`·`/api/dating/**`·`/api/wallet/**` 에만 건다
- 로그인은 **JWT** 다. 토큰은 **HttpOnly 쿠키**(`wks_token`)로 내려간다(2026-09-25, Bearer 헤더에서 전환 — 의도적 결정, `docs/handoff.md` 참고). **Spring Security·서버 쪽 세션 저장소는 여전히 안 쓴다** — 쿠키는 JWT를 담는 그릇일 뿐, 세션이 아니다. CSRF는 `SameSite=Lax` + 상태변경 API는 전부 POST/PATCH로 막는다(별도 CSRF 토큰 없음)
- `member` 는 `kakao_id` 만 가진다. 카카오 프로필·access token 을 저장하지 않는다. 계정당 결과는 1개(`result.member_id`)
- `resultId`·`shareId` 는 **UUIDv4**. 순번 금지 (예외는 `docs/architecture.md` §4 에 적힌 `compatibility.id` 하나)
- 소개팅에서 **잠긴 필드는 서버가 응답에서 뺀다.** 프론트 CSS 블러에 맡기지 않는다. 사진 URL 은 추측 불가하게 준다
- 로그에 생년월일시·이메일·카카오 id·토큰·JWT 를 남기지 않는다 (`resultId`·`memberId` 만)
- **시크릿은 환경변수.** `.env*.example`·`application-*.yml` 을 포함해 저장소 어디에도 실제 값을 넣지 않는다

**사주·LLM**
- **등급·점수는 코드가 계산하고, 문장만 LLM 이 쓴다.** `saju/` 는 스프링 컨텍스트 없는 순수 모듈로 둔다
- **LLM 프롬프트에 생년월일·시간·닉네임을 넣지 않는다.** 팔자·점수·관계유형만 넣는다
- LLM 호출은 항목별로 쪼개지 말고 **한 번에 묶는다.** `CallBudget` 을 우회하지 않는다. 테스트에서는 반드시 목킹한다

**실(재화)**
- 모든 증감은 원장(`thread_ledger`)에 기록한다. `ref_id` 는 NOT NULL 이고 중복은 DB UNIQUE 로 막는다. "조회 후 삽입"만으로는 동시 요청에서 뚫린다

## 코드 규칙 (요약, 전체는 convention.md)

- 도메인형 패키지. Controller 는 HTTP 만, 로직·트랜잭션은 Service. **Controller 는 Entity 를 반환하지 않는다** (DTO 는 `record`)
- Entity 에 `@Setter`·`@Data` 금지, 연관관계는 `LAZY`, enum 은 `EnumType.STRING`
- 예외는 `BusinessException` + `ErrorCode`. `ErrorCode` 는 `api-spec.md` 표와 1:1 이고, 추가 전에 `handoff.md` 표에 적는다
- **금지어**: `user`(회원은 `Member`), `match`, `fortune`, 클래스명 `saju`, 단독 클래스명 `Thread`
- **Flyway**: `ddl-auto: validate`. 머지된 파일은 수정하지 않고, 번호는 `handoff.md` 예약 표에 **먼저** 적는다. **같은 번호가 둘이거나, 낮은 번호가 높은 번호보다 늦게 머지되면 앱이 뜨지 않는다.** dev 를 머지한 뒤 `ls src/main/resources/db/migration` 으로 확인한다. 시각은 `TIMESTAMPTZ`
- 주석은 **왜**를 쓴다. 들여쓰기 4칸, LF, 파일 끝 개행
- 커밋은 `git add -A` 대신 **변경 파일을 지정**한다 (시크릿이 딸려 들어간 사고가 있었다)

## 테스트·실행

```
./gradlew test
docker compose up -d                                       # PostgreSQL 16
./gradlew bootRun --args='--spring.profiles.active=local'
```

- DB 테스트는 Testcontainers(PostgreSQL). **H2 금지.** 문법이 달라 로컬은 통과하고 배포에서 깨진다
- LLM·카카오·S3 같은 외부 호출은 테스트에서 실제로 부르지 않는다
- `application-local.yml` 은 커밋하지 않는다 (`.example` 만)

## 작업 흐름

이슈 → 브랜치(`dev` 에서 딴다, `feat/<이슈번호>-<설명>`) → PR(**base 는 `dev`**). 이슈 없는 브랜치는 만들지 않는다. 자세한 건 `docs/git-workflow.md`.
현재 **`dev` 에 push 하면 테스트 없이 EC2 로 자동 배포된다** (`.github/workflows/deploy.yml`, PR 용 CI 는 없다). 머지 전에 로컬에서 `./gradlew test` 를 돌리고, 깨진 마이그레이션이나 시크릿을 dev 로 보내지 않는다.
커밋 메시지는 한국어 `type: 내용`, 50자 이내. AI 가 쓴 문구를 그대로 쓰지 않는다. AI 가 만든 코드도 PR 리뷰를 거친다.

## Spring Boot 4 주의

AI 도구의 학습 데이터 대부분이 Boot 3 기준이다. 생성 결과를 그대로 믿지 않는다.
Jackson 3(Jackson 2 전용 API 는 깨진다), springdoc 은 3.x, Boot 3 에서 deprecated 였던 API 는 제거됐고 starter 이름도 바뀌었을 수 있다.

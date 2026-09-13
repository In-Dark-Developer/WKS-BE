# wks — 축제 사주 매칭 서비스 (Backend)

축제 기간 한정 사주 기반 매칭 웹서비스의 **백엔드 API 서버**.
컨셉: **"부처가 점지해준 나의 운명"** — 결과 텍스트의 화자는 '보살'.

- 백엔드 3명 (이 레포) · 프론트엔드 3명 (별도 레포)
- 두 팀의 접점은 `docs/api-spec.md` 뿐이다

---

## 새로 합류했다면

1. **`AGENTS.md`** — 규칙 원본. AI 도구도 이 파일을 읽는다. 제일 먼저
2. **`docs/architecture.md`** — 패키지 구조 · DB 스키마 · 설계 결정
3. **`docs/api-spec.md`** — 프론트와의 계약서
4. **`docs/convention.md`** — 코드 컨벤션
5. **`docs/git-workflow.md`** — 브랜치 · PR
6. **`docs/handoff.md`** — 팀이 지금 어디까지 왔는지
7. **`docs/backend-requirements.md`** — 1차 릴리즈에 뭘 만들고 무엇으로 판정하는지

작업 시작 전과 끝난 후에는 **매번 `docs/handoff.md`** 를 읽고 쓴다.

브랜치: `main`(프로덕션) ← `dev`(통합) ← `feat/<이슈번호>-<설명>`.
**이슈를 먼저 파고, PR base는 `dev`.**

---

## AI 도구 설정

세 명이 서로 다른 도구를 쓴다. **규칙 원본은 루트 `AGENTS.md` 하나다.**

| 파일 | 읽는 도구 | 내용 |
|---|---|---|
| `AGENTS.md` | Codex, Cursor, Copilot, Gemini CLI, Windsurf, Aider, Zed 등 | **규칙 원본** |
| `CLAUDE.md` | Claude Code | `@AGENTS.md` 한 줄 |
| `.cursor/rules/project.mdc` | Cursor | 포인터 |
| `.github/copilot-instructions.md` | GitHub Copilot | 포인터 |
| `<패키지>/AGENTS.md` | 전부 (가장 가까운 파일 우선) | 담당자별 규칙 |

**규칙을 도구별 파일에 복붙하지 마라.** 복붙하면 몇 주 안에 서로 달라진다.
규칙을 바꿀 땐 `AGENTS.md` 를 고친다.

---

## 담당

| 담당 | 패키지 | 범위 |
|---|---|---|
| 차은호 | `saju/` | 만세력 · 점수 로직 · LLM 프롬프트 |
| 최선우 | `result/`, `compatibility/` | FE 연동 API · 궁합 · 캐싱 |
| 곽도윤 | `signup/`, `common/`, 인프라 | 메일 · 외부 연동 · 배포 · 보안 |

**남의 패키지는 수정하지 않는다.** 필요하면 담당자에게 말한다.

---

## 스택

Java 17 · Spring Boot 4.1.1 · Gradle · PostgreSQL 16 · JPA · Flyway · Spring Mail
springdoc-openapi 3.1.1 · Lombok · Testcontainers · google-genai (Gemini Flash)

패키지 루트: `com.darkness.wks`

---

## 로컬 실행

### 요구사항

JDK 17, Docker

### 1. DB 띄우기

```bash
docker compose up -d
```

### 2. 로컬 설정

`src/main/resources/application-local.yml.example` 을 복사해
`application-local.yml` 로 만들고 값을 채운다.
**이 파일은 `.gitignore` 대상이다. 커밋하지 마라.**

### 3. 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/api/health

---

## 자주 나는 문제

| 증상 | 원인 / 해결 |
|---|---|
| `Schema-validation: missing table` | Flyway 미실행. `docker compose down -v` 후 재시작 |
| `Unsupported Database: PostgreSQL 16` | `flyway-database-postgresql` 의존성 누락 |
| 기동 시 springdoc 관련 에러 | springdoc 2.x 사용 중. **3.1.1로 올릴 것** (Boot 4 필수) |
| Swagger 파라미터가 `arg0`으로 표시 | Gradle compiler args에 `-parameters` 누락 |
| 결과 생성이 20초 넘게 걸림 | 해석 캐시 미동작. `reading` 테이블 확인 |
| 프론트에서 CORS 에러 | `WebConfig` 의 allowed origin 확인 |
| 404에 HTML이 뜬다 | `server.error.whitelabel.enabled: false` 확인 |
| 커넥션 풀 고갈 | `open-in-view: false` 확인 |
| 로컬 테스트는 통과, 배포하면 SQL 오류 | H2 사용 중. Testcontainers로 전환 |

---

## 1차 릴리즈 범위

사주 입력 → 만세력 계산 → 해석 5종 → 결과 조회 → 친구 궁합 → 소개팅 사전등록(메일 인증)

### 하지 않는 것

로그인 · 매칭 배치 · 실시간 채팅 · 관리자 API · 결과 수정 · 이미지 생성

**목록에 손대고 싶으면 팀에 먼저 말한다.**

---

## 개인정보

- 사주 단계는 **익명**. 생년월일시를 이메일과 묶지 않는다
- **이름·전화번호는 받지 않는다.** 스키마에 컬럼도 없다
- 로그에 생년월일시·이메일을 평문으로 남기지 않는다
- 축제 종료 +2주 전량 파기
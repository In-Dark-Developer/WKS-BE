# todo.md — 팀원 합류 전 체크리스트

새로 코드를 받은 팀원이 **작업을 시작하기 전에** 확인/처리해야 할 것들.
현재 상태 요약과 팀 차원에서 아직 안 풀린 결정 사항 위주로 정리했다.
(상세 기록은 `docs/handoff.md`, 요구사항은 `docs/backend-requirements.md` 참고)

---

## 0. 문서부터 읽기 (순서대로)

- [ ] `AGENTS.md` — 규칙 원본
- [ ] `docs/architecture.md` — 패키지 구조 · DB 스키마
- [ ] `docs/api-spec.md` — 프론트와의 계약서
- [ ] `docs/convention.md` — 코드 컨벤션
- [ ] `docs/git-workflow.md` — 브랜치 · PR 규칙
- [ ] `docs/handoff.md` — 팀 진행 상황 (작업 시작 전/후 **매번** 읽고 쓴다)
- [ ] `docs/backend-requirements.md` — 1차 릴리즈 범위 · 판정 기준

## 1. 로컬 환경 세팅

- [ ] JDK 17, Docker 설치 확인
- [ ] `docker compose up -d` 로 Postgres 16 기동
- [ ] `src/main/resources/application-local.yml.example` 을
      `application-local.yml` 로 복사 (이미 `.gitignore` 대상, 커밋 금지)
- [ ] 아래 값 채우기
  - [ ] `DB_PASSWORD` (docker-compose 기본값과 일치시킬 것)
  - [ ] `MAIL_USERNAME` / `MAIL_PASSWORD` (SMTP 계정 — 아래 3번 "미결정" 참고, 임시로 본인 계정 써도 무방)
  - [ ] `GOOGLE_API_KEY` (Gemini Flash 무료 티어)
- [ ] `./gradlew bootRun --args='--spring.profiles.active=local'` 로 기동 확인
  - API: http://localhost:8080
  - Swagger: http://localhost:8080/swagger-ui.html ✅ (springdoc 3.1.1 이미 붙어있음, 기동만 하면 됨)
  - Health: http://localhost:8080/api/health
- [ ] Flyway 마이그레이션(`V1__init.sql`)이 정상 적용되는지 확인 (테이블 5개: result, reading, compatibility, signup, email_verification)

## 2. 담당 확인

| 담당 | 패키지 | 범위 |
|---|---|---|
| 차은호 | `saju/` | 만세력 · 점수 로직 · LLM 프롬프트 |
| 최선우 | `result/`, `compatibility/` | FE 연동 API · 궁합 · 캐싱 |
| 곽도윤 | `signup/`, `common/`, 인프라 | 메일 · 외부 연동 · 배포 · 보안 |

- [ ] 본인 담당 패키지 확인, **남의 패키지는 건드리지 않기** (필요하면 담당자에게 말할 것)
- [ ] 이슈 먼저 파고 시작 (`feat/<이슈번호>-<설명>`, PR base는 `dev`)

## 3. 팀 차원에서 아직 안 풀린 결정 (작업 전에 확인/조율 필요)

`docs/backend-requirements.md` §15, `docs/handoff.md` 기준. 본인 작업이 이 항목에 걸리면 임의로 정하지 말고 팀에 먼저 확인할 것.

| 항목 | 목표 시점 | 담당 | 비고 |
|---|---|---|---|
| 만세력 라이브러리 선정 | Day 1 | 차은호 | **직접 구현 금지.** 미정이면 `saju/` 전체가 대기 — 프로젝트 최대 리스크 |
| 프론트 배포 도메인 (CORS용) | Day 1 | 곽도윤 | 프론트 팀 확인 필요 |
| 개발 서버 별도 운영 여부 | Day 1 | 곽도윤 | 없으면 프론트가 프로덕션에 바로 붙는 상황 발생 |
| 축제 D-day 확정 | — | 곽도윤 | 학생처 확인, 일정 역산의 기준점 |
| 학교 웹메일 도메인 화이트리스트 | Day 3 | 곽도윤 | `INVALID_EMAIL_DOMAIN` 검증에 필요 |
| SMTP 발송 계정 + 일일 한도 | Day 3 | 곽도윤 | 확보 전까지 로컬은 본인 계정으로 임시 대체 |
| Gemini 무료 티어 RPM·RPD 실측 | Day 6 | 차은호 | 부하 테스트 필요 |

- [ ] `main`/`dev` 브랜치 보호 설정 여부 확인 (`handoff.md` 기준 아직 ❌)
- [ ] `docs/api-spec.md` 프론트 전달 여부 확인 (아직 ❌ — 전달 안 됐으면 우선순위 높음)
- [ ] CORS `localhost:3000` 허용 여부 확인 (아직 ❌)

## 4. 코드 작업 전 상태 요약 (참고용)

- ✅ DB 스키마 5개 테이블 + Entity 전부 구현 완료 (`docs/architecture.md`와 완전히 일치 확인됨)
- ✅ `ErrorCode` 8종 전부 구현됨
- ✅ Swagger(springdoc 3.1.1) 연결 완료
- ⚠️ `SajuCalculator` / `ReadingScorer` / `ReadingGenerator` 등 핵심 로직은 파일만 존재 — 절기 보정·진태양시, LLM 캐싱/프롬프트 규칙(생년월일·닉네임 미포함, 5개 카테고리 한 번에 호출) 등 **내용 검증은 아직 안 됨**. 담당자가 작업하면서 직접 확인할 것
- ⚠️ `docs/handoff.md` "현재 상태" 표가 실제 구현보다 뒤처져 있음 — 작업 시작 전에 최신화할 것

## 5. 잊지 말 것

- 작업 끝나면 `docs/handoff.md`에 기록 (템플릿 그대로, 최신 항목이 위로)
- Flyway 번호는 파일 만들기 전에 `handoff.md`의 "Flyway 번호 예약" 표에 먼저 적기
- `ErrorCode` 추가 시 `handoff.md`의 "ErrorCode 추가 현황" 표에 기록 + api-spec 반영
- `resultId`는 반드시 UUIDv4 (순번 금지)
- 이름·전화번호는 받지 않음 — 스키마에 컬럼도 없음, 추가하지 말 것
- 점수는 코드가 계산, 문장만 LLM이 생성 — 이 경계 흐리지 말 것

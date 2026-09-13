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
| 릴리즈 D-day | (미정) |
| `main`·`dev` 브랜치 생성 + 보호 설정 | ❌ |
| main(프로덕션) 배포 상태 | ⚠️ 파이프라인 코드는 준비됨, EC2 인스턴스 없음 (미배포) |
| `/api/health` (배포 도메인) | ❌ |
| Flyway 최신 버전 | (없음) |
| api-spec 프론트 전달 | ❌ 미전달 |
| CORS localhost:3000 허용 | ❌ |

### 지금 막혀 있는 것

| 내용 | 담당 | 필요한 것 |
|---|---|---|
| 만세력 라이브러리 선정 | 차은호 | **Day 1 내 결론** |
| Gemini 무료 티어 RPM·RPD 실측 | 차은호 | Day 6 부하 테스트 |
| 프론트 배포 도메인 (CORS용) | 곽도윤 | 프론트 팀 확인 |
| 축제 D-day 확정 | 곽도윤 | 학생처 확인 |
| 개발 서버 별도 운영 여부 | 곽도윤 | Day 1 결정 |
| SMTP 발송 계정 | 곽도윤 | 발송 한도 확인 필요 |

---

## Flyway 번호 예약

**파일 만들기 전에 여기 먼저 적는다.** 번호 충돌은 가장 자주 나는 사고다.

| 번호 | 예약자 | 내용 | 상태 |
|---|---|---|---|
| V1 | 곽도윤 | init schema (5개 테이블) | 예정 |

---

## ErrorCode 추가 현황

`common/exception/ErrorCode.java` 는 3명이 다 건드린다. 추가 전 여기 적는다.

| code | 추가자 | api-spec 반영 |
|---|---|---|
| (문서 기준 8종) | - | ✅ |

---

## 프론트에 공지한 API 변경

| 날짜 | 변경 내용 | 공지함 |
|---|---|---|
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
  **프롬프트에 생년월일·닉네임을 넣지 말고 팔자와 점수만 보낸다**
- 무료 티어 RPM 제한 때문에 **5개 카테고리를 한 번의 호출로 생성**한다. 개별 호출 금지
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
# git-workflow.md

백엔드 3인. **이슈 기반 브랜치 → dev 병합 → dev를 main으로 릴리즈** 구조.

> **⚠️ 현재 운영 상태 (2026-09-26).** 아래 본문은 목표 구조이고, 실제와 다른 부분은 ⚠️ 로 표시했다.
>
> - **`dev` push → 개발 서버(`api-dev.threadoffate.site`), `main` push → 운영(`api.threadoffate.site`).** 개발 서버는 2026-09-26 EC2 에 떠서 동작 확인했다. 자세한 건 아래 "CI/CD 매핑"
> - **PR CI 는 있다** (`ci.yml`, `dev`·`main` 대상 PR 에서 `./gradlew build`). 다만 브랜치 보호가 없어 CI 가 빨간불이어도 머지는 막히지 않는다. 배포 이미지는 여전히 `bootJar -x test` 라 **배포 단계는 테스트를 안 돌린다** — CI 결과를 보고 머지한다
> - **브랜치 보호 설정은 할 수 없다** (private 저장소 무료 플랜, handoff 현재 상태). PR 리뷰가 유일한 방어선이다

---

## 브랜치 구조

```
main                    ⚠️ 목표: 프로덕션 서버. 현재는 배포되지 않는다
 └── dev                통합 브랜치. 모든 작업이 여기로 모인다
      ├── feat/12-saju-calculator
      ├── feat/15-compatibility-api
      └── fix/18-hour-unknown-null
```

| 브랜치 | 역할 | 직접 푸시 |
|---|---|---|
| `main` | 릴리즈 기록. ⚠️ 목표는 **프로덕션**이지만 현재는 배포되지 않는다 | 금지 (핫픽스 예외) |
| `dev` | 통합. ⚠️ 현재 **push 가 곧 운영 배포**다 | 금지 (PR로만) |
| `feat/*`, `fix/*` | 개인 작업 | 자유 |

**작업 브랜치는 항상 `dev` 에서 딴다.**
`main` 에서 따면 dev에 이미 머지된 남의 변경이 빠진 채로 시작해서, PR 올릴 때 충돌이 난다.

---

## 이슈 → 브랜치 → PR

### 1. 이슈부터 판다

작업 시작 전에 GitHub Issue를 만든다. 이슈 번호가 브랜치명과 PR에 들어간다.

이슈에 쓸 것:
- 무엇을 하는지 (한 줄)
- 담당자 assign
- 라벨: `backend` + `saju` / `result` / `compatibility` / `signup` / `auth` / `member` / `dating` / `wallet` / `infra`
- **관련 요구사항 ID** (`FR-SJ-02` 등) — `docs/backend-requirements.md` 참조

요구사항 ID를 적는 게 핵심이다. 나중에 "이거 왜 이렇게 만들었지"를 문서로 되짚을 수 있다.

### 2. 브랜치명

```
<타입>/<이슈번호>-<간단한-설명>

feat/12-saju-calculator
fix/18-hour-unknown-null
chore/23-github-actions
docs/27-api-spec-update
```

| 타입 | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `chore` | 설정·의존성·인프라 |
| `docs` | 문서 |
| `test` | 테스트만 추가 |
| `refactor` | 동작 변경 없는 구조 개선 |

소문자 + 하이픈. 한글 브랜치명은 만들지 않는다(도구 호환 문제).

### 3. 작업 → push → PR

```bash
git switch dev && git pull
git switch -c feat/12-saju-calculator

# 작업 + 커밋
git push -u origin feat/12-saju-calculator
# → PR 생성, base: dev
```

**PR의 base가 `dev` 인지 반드시 확인한다.** GitHub 기본값이 default branch라 실수하기 쉽다.
→ 저장소 설정에서 **default branch를 `dev` 로 바꿔두면** 이 실수가 사라진다.

PR 본문에 `Closes #12` 를 쓰면 머지 시 이슈가 자동으로 닫힌다.

### 4. 리뷰 → 머지

- 리뷰어 1명 승인 후 머지 (3명이라 순환 리뷰가 자연스럽다)
- **Squash and merge**
- 머지 후 브랜치 삭제
- CI(빌드 + 테스트) 통과 필수 — ⚠️ **아직 CI 가 없다.** 머지 전에 로컬에서 `./gradlew test` 를 돌리고 PR 에 결과를 적는다
- **500줄 이하** 목표. 넘으면 이슈를 쪼갠다

---

## dev → main 릴리즈

> ⚠️ 현재 `main` 으로 배포하는 워크플로가 없다. 운영 배포는 `dev` push 로 이미 일어난다. 이 절은 `main` 배포를 구성한 뒤의 절차다.

`main` 은 프로덕션이므로 **아무 때나 머지하지 않는다.**

```
PR 생성: base main ← compare dev
```

| 항목 | 규칙 |
|---|---|
| 시점 | 기능 단위 완성 + 검증 통과 시 |
| 머지 방식 | **Merge commit** (squash 금지 — dev의 커밋 이력을 보존한다) |
| 승인 | 리뷰어 1명 + 도윤 확인 |
| PR 제목 | `release: <날짜> <주요 내용>` |
| 필수 | `docs/backend-requirements.md` 의 해당 인수 조건 통과 |

릴리즈 PR 본문에 **포함된 이슈 번호 목록**을 적는다.
장애 발생 시 어디까지 롤백할지 판단이 빨라진다.

### 태그

`main` 머지 후 태그를 단다.

```bash
git tag -a v0.1.0 -m "사주 계산 + 결과 조회"
git push origin v0.1.0
```

**롤백은 태그 단위로 한다.** 태그가 없으면 어느 커밋으로 돌아갈지부터 헤맨다.

---

## 핫픽스

축제 당일 프로덕션 장애는 예외 경로다.

⚠️ 현재 운영 배포는 `dev` 기준이라 핫픽스도 **`dev` 로 머지돼야 반영**된다.

```
main → hotfix/<이슈번호>-<설명> → main (PR, 빠른 승인)
                                → dev 에도 반드시 머지
```

**`dev` 에 되돌려 머지하는 걸 잊지 마라.** 안 하면 다음 릴리즈에서 수정이 사라진다.

핫픽스를 했으면 **즉시 팀 채널에 알리고 `docs/handoff.md` 에 기록한다.**

---

## 하루 루틴

```
아침
  git switch dev && git pull
  docs/handoff.md 읽기          ← 어제 무슨 일이 있었는지
  이슈 확인 → git switch -c feat/<번호>-<설명>

작업 중
  자주 커밋. 하루 한 번 몰아서 커밋하지 않는다
  dev 가 움직였으면:  git fetch origin && git rebase origin/dev

퇴근 전
  push → PR (base: dev). 미완성이면 Draft PR
  docs/handoff.md 에 기록 추가
```

**브랜치를 3일 이상 살려두지 않는다.** 길수록 충돌이 기하급수로 커진다.

---

## 브랜치 보호 설정 (Day 1 · 도윤)

> ⚠️ private 저장소 무료 플랜이라 **지금은 설정할 수 없다** (handoff 현재 상태). 아래는 유료 전환·public 전환 시의 목표 설정이다. 지금은 PR 리뷰로 대체한다.

GitHub → Settings → Branches

**`main`**
- Require a pull request before merging (승인 1명)
- Require status checks to pass (CI)
- 핫픽스 대비로 관리자 예외는 열어둔다

**`dev`**
- Require a pull request before merging (승인 1명)
- Require status checks to pass (CI)

**Default branch를 `dev` 로 변경** — PR base 실수 방지.

---

## CI/CD 매핑

> **확정 (2026-09-23).** 개발 서버를 따로 둔다(`api-dev.threadoffate.site`, 같은 EC2·같은 postgres·nginx 컨테이너 공유,
> `docker-compose.dev.yml` + `/opt/wks-dev`). 브랜치 → 배포 대상은 1:1로 고정: `main` → 운영, `dev` → 개발 서버.
> 자세한 절차는 `docs/runbook-dev-server.md`.

| 이벤트 | 동작 |
|---|---|
| `dev`·`main` 대상 PR 생성·갱신 | `ci.yml`: `./gradlew build`(컴파일 + 테스트). 배포 없음 |
| `dev` push | `deploy-dev.yml`: Docker 이미지 빌드 → `:dev` 태그로 GHCR push → `/opt/wks-dev`에서 `docker compose -p wks-dev` 로 배포 → `api-dev.threadoffate.site/api/health` 헬스체크 |
| `main` push | `deploy.yml`: Docker 이미지 빌드 → `:latest` 태그로 GHCR push → `/opt/wks`에서 배포. 트리거를 `dev`→`main`으로 바꾸는 변경은 **적용 완료**(2026-09-23, 곽도윤 확인) |
| 태그 push | ⚠️ 현재 없음. (목표: 이미지 태깅) |

**⚠️ 머지 직후 공백 주의**: `deploy.yml` 트리거 변경이 `dev`에 머지되는 순간부터 **`dev` push는 더 이상 운영을
배포하지 않는다** (deploy-dev.yml만 반응, 개발 서버로 감). 운영 배포는 이제 `dev`→`main` PR 머지로만 일어나는데,
**아직 한 번도 `main`으로 릴리즈해본 적이 없다** — `main` 브랜치엔 이 워크플로 자체가 없던 상태였다. 즉 머지 후
첫 `dev`→`main` 릴리즈 PR을 만들어 병합하기 전까지는 운영에 새 커밋이 전혀 배포되지 않는 공백이 생긴다.
기존 운영 컨테이너는 계속 떠 있으니 서비스 중단은 아니지만, **핫픽스가 필요하면 이 공백 중엔 자동 배포가
안 된다는 걸 팀이 알고 있어야 한다.** 이 변경을 머지하면 가급적 빨리 첫 `dev`→`main` PR을 만들어 파이프라인이
실제로 동작하는지 확인할 것 — `docs/handoff.md`에도 남겨둠.

**배포가 자동으로 안 하는 것** (둘 다 EC2 에서 손으로 한다, `docs/runbook-dev-server.md`):
- **`.env` 는 어떤 배포도 만들거나 고치지 않는다.** 앱이 새 환경변수를 읽게 되면 ① 운영은 `docker-compose.prod.yml` 의 `app.environment` 에도 추가하고(운영은 `env_file` 을 안 써서 여기 없으면 컨테이너에 안 들어간다) ② EC2 의 `/opt/wks/.env`·`/opt/wks-dev/.env` 에 값을 넣는다. 개발은 `env_file: .env` 라 ②만 하면 된다
- **nginx 가 읽는 `nginx/api-dev.conf.active` 는 배포가 갱신하지 않는다.** `api-dev.conf` 를 고치면 `main` 릴리즈 후 runbook 11단계로 반영한다

**롤백을 실제로 한 번 해본다.** 문서로만 써두면 당일에 작동하지 않는다.

### GitHub Secrets

**운영(`deploy.yml`)**: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_APP_DIR`. 앱 시크릿(`DB_PASSWORD`, `GOOGLE_API_KEY`, `MAIL_*` 등)은 GitHub 가 아니라 **EC2 의 `/opt/wks/.env`** 에 둔다 (`docker-compose.prod.yml`)

**개발(`deploy-dev.yml`)**: 같은 EC2·같은 SSH 키라 `EC2_HOST`·`EC2_USER`·`EC2_SSH_KEY`를 그대로 재사용한다.
배포 경로(`/opt/wks-dev`)·컴포즈 프로젝트명(`wks-dev`)은 시크릿으로 안 두고 워크플로에 고정값으로 박아뒀다 —
시크릿 값이 잘못 바뀌어 운영 경로를 가리키는 사고를 막기 위해서다. 앱 시크릿도 운영과 같은 패턴으로
**EC2 의 `/opt/wks-dev/.env`** 에 둔다(`DEV_GOOGLE_API_KEY` 같은 GitHub Secrets는 만들지 않는다 — GitHub Actions가
`.env`를 만들거나 건드리지 않으므로 애초에 쓸 곳이 없다).

---

## 커밋 메시지

```
feat: 사주 결과 생성 API 구현
fix: 시간 모름 입력 시 시주 계산 오류 수정
docs: api-spec 궁합 응답 형식 수정
chore: Flyway PostgreSQL 모듈 추가
test: 궁합 점수 대칭성 테스트 추가
```

- 제목 한국어, 50자 이내, 마침표 없음
- **AI가 생성한 커밋 메시지를 그대로 쓰지 마라.** 본인 말로 쓴다
- 본문에 `#12` 를 쓰면 이슈에 자동 연결된다

---

## 충돌이 잦은 파일

여러 명이 동시에 건드리기 쉽다. **수정 전 팀 채널에 알린다.**

| 파일 | 이유 |
|---|---|
| `build.gradle` | 의존성 추가 |
| `application.yml` | 공통 설정 |
| `db/migration/V*.sql` | **번호 충돌** |
| `common/` 전체 | 공통 응답·예외 |
| `ErrorCode.java` | 3명이 다 추가한다 |
| `AGENTS.md` (루트) | 규칙 원본 |

**Flyway 번호와 ErrorCode는 선점제다.** 쓰기 전에 `docs/handoff.md` 예약 표에 적는다.

---

## .gitignore

```gitignore
# 로컬 설정 — 각자 DB 비밀번호가 다르다
application-local.yml
.env

# 빌드
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store

# 로그
*.log
logs/
```

AI 도구가 만드는 로컬 캐시 파일은 각자 추가한다.
단 **`.cursor/rules/` 와 `.github/copilot-instructions.md` 는 커밋한다.** 팀 공용 포인터다.

**시크릿을 한 번이라도 커밋하면 히스토리에서 지워도 남는다.**
실수했으면 즉시 해당 비밀번호·키를 교체하고 팀에 알린다. 조용히 넘어가지 않는다.

---

## 프론트 팀과의 협업

레포가 다르므로 코드 충돌은 없다. 대신 **계약 충돌**이 난다.

- API를 바꾸면 `docs/api-spec.md` 수정 → **공용 채널 공지** → `docs/handoff.md` 기록
- 필드 삭제·타입 변경은 사전 합의. 추가는 자유
- **Day 1에 api-spec 확정본을 넘기는 게 최우선.** 없으면 프론트 3명이 착수 불가
- **CORS에 프론트 로컬 origin 허용**이 안 되면 프론트가 아무것도 못 한다
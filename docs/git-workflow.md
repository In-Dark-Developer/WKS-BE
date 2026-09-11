# git-workflow.md

백엔드 3인. **이슈 기반 브랜치 → dev 병합 → dev를 main으로 릴리즈** 구조.

---

## 브랜치 구조

```
main                    프로덕션 서버. 배포되는 브랜치
 └── dev                통합 브랜치. 모든 작업이 여기로 모인다
      ├── feat/12-saju-calculator
      ├── feat/15-compatibility-api
      └── fix/18-hour-unknown-null
```

| 브랜치 | 역할 | 직접 푸시 |
|---|---|---|
| `main` | **프로덕션.** 항상 동작해야 한다 | 금지 (핫픽스 예외) |
| `dev` | 통합. 작업물이 합쳐지는 곳 | 금지 (PR로만) |
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
- 라벨: `backend` + `saju` / `result` / `compatibility` / `signup` / `infra`
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
- CI(빌드 + 테스트) 통과 필수
- **500줄 이하** 목표. 넘으면 이슈를 쪼갠다

---

## dev → main 릴리즈

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

| 이벤트 | 동작 |
|---|---|
| `feat/*` → `dev` PR 생성·갱신 | `./gradlew build` (컴파일 + 테스트) |
| `dev` 머지 | 빌드 + 테스트 (+ 개발 서버 배포, 두는 경우) |
| `dev` → `main` 머지 | 빌드 → Docker 이미지 → **EC2 프로덕션 배포** |
| 태그 push | 이미지 태깅 |

**Day 1에 도윤이 이 파이프라인을 뚫는다.**
Day 1 끝에 `/api/health` 가 프로덕션 도메인에서 200을 반환해야 한다.
배포를 마지막 날로 미루면 그날 하루가 배포 삽질로 사라진다.

**롤백을 Day 6에 실제로 한 번 해본다.** 문서로만 써두면 당일에 작동하지 않는다.

> **미결정**: 개발 서버를 따로 둘지. EC2를 하나 더 띄우면 비용이 늘고,
> 안 띄우면 `dev` 는 CI만 돌고 배포는 안 된다.
> 안 두는 경우 프론트는 프로덕션(`main`)에 붙어야 하므로, **릴리즈 주기가 프론트 작업 속도를 좌우한다.**
> Day 1에 도윤이 결정한다.

### GitHub Secrets

`DB_PASSWORD`, `GOOGLE_API_KEY`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `EC2_SSH_KEY`, `EC2_HOST`

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
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
| main(프로덕션) 배포 상태 | ❌ 미배포 |
| `/api/health` (배포 도메인) | ❌ |
| Flyway 최신 버전 | (없음) |
| api-spec 프론트 전달 | ❌ 미전달 |
| CORS localhost:3000 허용 | ❌ |

### 지금 막혀 있는 것

| 내용 | 담당 | 필요한 것 |
|---|---|---|
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
| V2 | 최선우 | 운명·등급·행운 콘텐츠 저장을 위한 reading 확장 | 완료 |
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

### 2026-09-13 (일) · 차은호 · 문서 (#7, 기획 명세 반영) · Claude Code

**한 일**
- 프론트 기능명세서(Figma v0.2, 9/13)와 기획 결정을 백엔드 문서에 반영
- 궁합 Tier 구간 변경 기록: 귀인 90~100 / 찰떡 75~89 / 벗 61~74 / 스침 0~60. **코드(`CompatibilityCalculator`·`CompatibilityService`)는 최선우가 반영**

**건드린 파일/패키지**
- `docs/api-spec.md` §4, `docs/architecture.md` §7, `docs/backend-requirements.md` FR-CP-03·TR-06·인수 조건, `docs/convention.md`, `docs/handoff.md`

**다음 사람이 알아야 할 것 — 프론트 명세와 어긋나는 것 (팀 결정 필요)**
- **API 계약 불일치.** 프론트 명세는 `POST /readings`, `GET /shares/{shareId}`, `POST /shares/{shareId}/compatibility`, `GET /me/friends`, `POST /me/threads` 와 응답 타입 `Reading{id, shareId, zodiac, destiny, sections(3), cardGrades, lucky}` 를 쓰고, 원본을 "백엔드 저장소 `docs/api/openapi.yaml`" 이라고 적음. **우리 레포에 그 파일 없음.** 우리 계약은 `docs/api-spec.md` (`/api/results`, `resultId`, `destiny`, `fortunes`). 프론트 Open Question Q3 담당은 `@hairyung2002`. 9/17 전에 한쪽으로 맞춰야 연동 가능
- **요청 형식.** 프론트는 `birthDate` "숫자 8자리", 우리는 `yyyy-MM-dd`. 시진 선택 UI 인데 전송 규칙(가운데 시각, 자시 두 칸)이 프론트 문서에 없음 → api-spec §2 를 프론트에 공지해야 함
- **사전신청 수집 항목.** 프론트 FR-10 은 학교 이메일·이름·연락처(인스타/전화)·사진·학과·나이·MBTI·자기소개 를 Must 로 수집. 우리 규칙은 "이름·전화번호는 받지 않는다, 컬럼도 없다". `signup/` 스키마·개인정보 정책 결정 필요 (곽도윤)
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
- 기획 결정 대기 1건: 자시 두 칸 분리 (음력 지원·지역 제거·등급 6단계는 확정)
- 최선우: `CreateResultRequest` 에 `CalendarType calendarType`(saju 패키지 enum)·`Boolean isLeapMonth` 추가, `birthDate` 를 `String` 으로, `birthRegion` 삭제. `BirthDate.parse(...).toSolar()` 결과(양력)를 `analyze()` 와 `Result` 저장에 사용
- 최선우: 응답에 `zodiac` 추가 — `SajuPillars.zodiac()` (또는 저장된 `year_pillar` 로 `Zodiac.fromYearPillar`). 컬럼 추가 불필요
- `saju/` 출력 형태 미확정: `ReadingCategory` 5종(점수) vs `result/ResultAnalysisPort` (운명 + 결혼·자녀·연애 등급 + 행운 아이템·장소). 등급 문자열 집합·산출 기준도 미정. 이게 정해져야 `ReadingScorer`·`ReadingGenerator` 착수 가능
- TR-01 대체: 포스텔러 만세력 2.2 와 7건 대조(입춘 전후·자시·시진 경계·설날) 전부 일치, `SajuCalculatorTest.matchesPosteller` 에 고정. 실제 인물 데이터가 생기면 추가

**문서 변경**
- `docs/architecture.md`: 스택 표에 lunar-java 추가, §9 미결정에서 만세력 항목 제거, §6 에 계산 방식 반영
- `docs/api-spec.md` §2: 음력 필드 2개 추가, 시진 입력 규칙 추가 (필드 추가라 사전 합의 불필요, 프론트 공지 필요)

**프론트에 알려야 할 것** (기획 확인 후 공지, 2026-09-13 기획에 전달)
- 시간은 시진(2시간) 선택 UI 그대로. 프론트가 선택한 칸의 **가운데 시각**을 `birthTime: HH:mm` 으로 보낸다 (묘시 05:30~07:30 → `06:30`). 같은 시진이면 팔자가 같아서 문제 없음
- **자시는 두 칸으로 분리 요청**: `자시 00:00~01:30` → `00:45`, `자시 23:30~24:00` → `23:45`. 자정을 걸쳐서 날짜+자시만으로는 새벽/밤 구분이 안 되고, 둘은 일주가 하루 다름
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

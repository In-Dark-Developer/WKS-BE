# LLM 대체 템플릿 문장 개수 산정

> 2026-09-29 · 분석 문서 (구현 없음). 무료 키 → 유료 키 → **미리 쓴 템플릿** 3단 대체 중 3단계의 규모를 코드 기준으로 산정한다.
> 근거 코드: `saju/ReadingGenerator`, `saju/CompatibilityReasonGenerator`, `dating/DatingReasonGenerator`, `saju/GeminiJson`, `compatibility/CompatibilityCalculator`, `saju/ReadingScorer`, `resources/prompts/*.txt`, `db/migration/V1~V25`.

## 1. 요약

- **추천 수준: 템플릿 197개** = 사주 결과 108 + 친구 궁합 60 + 소개팅 29 (§4)
- **생성 호출 20회** (`⌈197 / 10⌉ = 20`), **검수 49분 15초** (`197 × 15초 = 2,955초`)
- 단, 추천 수준은 궁합지도 친구 5명이면 세 자리 중 하나라도 **완전히 같은 글**이 나올 확률이 45.9%, 앞부분 본문만 보면 97.4%다 (§5). 이 정도로 겹쳐도 되는지 먼저 정해야 한다

---

## 2. LLM 호출 지점 (1단계)

`google-genai` 호출은 `GeminiJson.generate()` 한 곳뿐이다 (`endpoint.client.models.generateContent`). 이 메서드를 부르는 생성기는 3개이고, 템플릿이 채워야 하는 자리(응답 JSON 필드)는 **9개**다.

| # | 호출 위치 | 화면·명세 ID | 프롬프트 입력 (필드 단위) | 응답 필드 = 템플릿 자리 | 호출 시점 | 캐시 |
|---|---|---|---|---|---|---|
| A | `ReadingGenerator.generate()` ← `SajuResultAnalysisAdapter.analyze()` ← `ResultService` (`POST /api/results`) | 결과 홈 3.1(운명 카드 설명), 3.5(잘 맞는 오행 이유), 3.8(세부 해설) | 성별 / 년·월·일·시주 글자(시주 모르면 "모름") / 나의 기운(일간 오행) / 많은 기운(글자 개수 최대, 동점이면 여러 개) / 없는 기운(개수 0, 여러 개 또는 "없음") / 배우자 기운(남 재성·여 관성) / 배우자 자리 기운(일지 오행) / 자녀 기운(남 관성·여 식상) / 잘 맞는 기운 + 그 뜻(`LuckyPlace.luckyElement` + `ROLE_MEANING`) / 결혼·자녀·연애 등급(6단계) | `destinyDescription`, `marriage`, `children`, `love`, `elementMatch` (5개) | 결과 생성 시 **동기**. 실패하면 결과 생성 전체가 `LLM_UNAVAILABLE` | `reading.destiny_content`, `marriage_content`, `children_content`, `love_content`, `element_match_content`. 같은 입력(생년월일·시간·성별)이고 `reading.version` 이 같으면 재사용 (#62) |
| B | `CompatibilityReasonGenerator.generate()` ← `CompatibilityReasonService.getReason()` (`GET /api/compatibilities/{id}/reason`) | 궁합지도 4.7, 공유 5.10 | 관계 유형(귀인·찰떡·벗·스침) / 궁합 점수 / A·B 팔자 글자 / A·B 일간 오행 / A·B 많은 기운 / 두 기운의 관계(같음·상생·상극, 방향 포함) | `why`, `together`, `conflict` (3개) | 상세를 **처음 열 때** (plan §1.2) | `compatibility.reason_why`, `reason_together`, `reason_conflict` |
| C | `DatingReasonGenerator.generate()` ← `DatingReasonService.generateAndSave()` / `fillRecipientReason()` | 소개팅 카드 "궁합 까닭" (plan §7, §1.2 끝) | 관계 유형 / 점수 / 보는 사람·상대 팔자 글자 / 각자 일간 오행 / 보는 사람 기준 관계(같음·살림·누름, 방향 있음) | `reason` (1개) | 보낸 사람 시점: 첫 REASON 해금 또는 요청 수락 뒤 비동기. 받은 사람 시점: 요청 커밋 뒤 비동기 | 보낸 사람: `dating_recommendation.reason_content` / 받은 사람: `dating_request.recipient_reason` (같은 생성기 → 템플릿 세트 1개) |

### 요청서의 10개 자리와 코드 대조

| 요청서 항목 | 코드 기준 | 템플릿 필요 |
|---|---|---|
| 연애운·결혼운·자녀운 | A `love`·`marriage`·`children` | 필요 |
| 잘 맞는 오행의 이유 | A `elementMatch` | 필요 |
| 행운 아이템 | **LLM 아님.** `DailyLucky` 가 `resources/lucky/items.txt` 풀에서 해시로 고른다 | 불필요 |
| 행운 장소 | **LLM 아님.** `LuckyPlace` 가 `resources/lucky/places.txt` 풀에서 고른다 | 불필요 |
| 인연 유형 해설 (5.9) | **백엔드 미구현.** `CompatibilityResponse` 에는 `score`·`tier`·닉네임뿐이다. 프론트 고정 문구로 추정 | 백엔드가 줘야 한다면 `tier 4 = 4개` (LLM 불필요, 합계 제외) |
| 왜 나에게 귀인일까요? / 둘이 만나게 된다면? / 둘이 싸움이 난다면? | B `why`·`together`·`conflict` | 필요 |
| (요청서에 없음) 운명 카드 설명 | A `destinyDescription` | **필요** |
| (요청서에 없음) 소개팅 궁합 까닭 | C `reason` | **필요** |
| (참고) 운명 제목 | LLM 아님. `DestinyTitle` 8종 (`destiny-titles.txt`) | 불필요 |

---

## 3. 키 가용성 (2단계)

**시주**: `SajuPillars.hourPillar` 는 시간 모름이면 `null` 이다 (입력 2.6 "태어난 시간을 몰라요"). 그래서 시주는 키로 쓰지 않는다.

| 키 | 경우의 수 | 지금 코드로 얻는 법 | 없으면 필요한 작업 |
|---|---|---|---|
| 일간 | 10 | `pillars.dayPillar().charAt(0)` (저장: `result.day_pillar`) | — |
| 일주 | 60 | `pillars.dayPillar()` | — |
| 월지 → 계절 | 4 | 월지는 `monthPillar().charAt(1)` | 계절 매핑이 코드에 없다. 12→4 표 한 줄 (인묘진 봄 / 사오미 여름 / 신유술 가을 / 해자축 겨울) |
| 가장 강한 오행 | 5 | `ReadingGenerator.elementCounts(p)` 의 최댓값 (화면·프롬프트와 같은 글자 개수 기준) | 동점이면 여러 개가 나온다. **동점은 `Element.strengths(p)` 가 큰 쪽**으로 하나를 고르는 규칙 필요. `elementCounts` 는 package-private 이라 공개 메서드 필요 |
| 가장 약한 오행 | 5 | 같은 배열. 프롬프트의 "없는 기운"은 개수 0인 것들이라 0개·여러 개가 가능 | "개수 최소 → 동점은 `strengths` 최소" 규칙으로 늘 하나. 또는 "없음" 포함 6가지 |
| 카테고리 점수 → 구간 | 3 | `ReadingScorer.score()` 0~100 (저장: `reading.marriage_score`·`children_score`·`love_score`). 기존 구간은 `Grade.of` 6단계, `DestinyTitle.level` 상/하 2단계 | 3등분 경계는 §6 SQL 결과로 정한다 |
| 두 사람의 일간 오행 관계 (방향) | 5 | `Element.roleFor(me)` (0 비겁 1 식상 2 재성 3 관성 4 인성) | — |
| 두 사람의 오행 쌍 (순서 무관) | 15 | `Element.ofStem()` 두 개를 ordinal 순으로 정렬 (`5 + C(5,2) = 5 + 10 = 15`) | — |
| 두 사람의 일지 관계 | 6 | **없음.** `CompatibilityCalculator` 안의 `private` 집합(`BRANCH_COMBINATIONS`·`CLASHES`·`HARMS`·`BREAKS`, `TRINES`)으로 점수만 낸다 | 일지 둘을 받아 관계 enum 하나를 돌려주는 공개 메서드. **형(刑)은 코드에 없다** — 쓰려면 삼형·자형 집합 추가 |
| 인연 유형 | 4 | `CompatibilityTier.fromScore()` (저장: `compatibility.tier`. 소개팅은 `dating_recommendation.score` 에서 계산) | — |

아래는 요청서 표에 없지만 **프롬프트가 이미 쓰는 입력**이라 키로 추가했다.

| 추가 키 | 경우의 수 | 얻는 법 |
|---|---|---|
| 성별 | 2 | `result.gender`. 배우자·자녀 기운이 성별로 갈리고 프롬프트가 역할어를 성별에 맞추게 한다 |
| 많은 기운의 역할 | 5 | 강한 오행 `.roleFor(일간 오행)` |
| 배우자 자리(일지)의 역할 | 5 | `Element.ofBranch(day.charAt(1)).roleFor(me)` |
| 자녀 기운 세기 | 3 | 자녀 기운(남 관성·여 식상) 글자 개수 0 / 1 / 2 이상. `buildPrompt` 안 지역변수라 추출 필요 |
| 잘 맞는 오행 × 그 뜻 | 5 × 5 = 25 | `LuckyPlace.luckyElement(p)` (public) + `.roleFor(me)` |
| 두 사람의 오행 순서쌍 | 5 × 5 = 25 | 소개팅용. 방향 관계 5 × 내 오행 5 와 같다 |

### 일지 관계 우선순위 제안

코드가 실제로 보는 관계는 **합 / 충 / 삼합 / 같은 지지 / 해 / 파 / 없음**이고, 형은 없다. 겹침은 `인해`·`사신` 두 쌍(합이면서 파)뿐이다.
144개 순서쌍(`12 × 12`)을 `branchScore()` 순서로 분류한 실측 분포:

| 관계 | 순서쌍 수 | 비율 |
|---|---|---|
| 없음 | 64 | 44.4% |
| 삼합 | 24 | 16.7% |
| 합 | 12 | 8.3% |
| 충 | 12 | 8.3% |
| 같은 지지 | 12 | 8.3% |
| 해 | 12 | 8.3% |
| 파 | 8 (겹치는 4개는 합으로) | 5.6% |

**제안: 점수 계산과 같은 순서 `합 > 충 > 삼합 > 같음 > 해·파 > 없음` 의 6가지.** 해·파는 둘 다 감점 0.5배로 점수상 같다.
글이 점수와 반대 방향을 말하지 않게 하려면 `branchScore()` 와 같은 순서여야 한다. 요청서의 `합/충/형/파/해/없음` 을 쓰려면 형 집합을 새로 넣어야 하고, 그러면 문장은 형을 말하는데 점수는 형을 모르는 어긋남이 생긴다.

---

## 4. 자리별 × 수준별 개수 (3단계)

### 설계 원칙

- **사주 결과 다섯 자리는 서로 다른 키**를 쓴다: 운명 설명 = 일간·월지, 연애 = 많은 기운, 결혼 = 일지, 자녀 = 자녀 기운 세기, 잘 맞는 오행 = 보완 오행. 프롬프트의 "연애는 배우자 기운에서, 결혼은 배우자 자리에서…" 규칙과도 맞는다
- 오행 이름은 역할 키로 묶고 `{myElement}` 같은 자리표시자로 넣는다 (§7). 그래서 "나무의 기운인 당신에게…"처럼 구체적인 글을 쓰면서 키 수를 5로 줄일 수 있다
- **방향**: 코드상 친구 궁합(B)은 **대칭**이다. 한 `compatibility` 행을 두 사람이 같이 보고, 프롬프트가 "당신"을 금지하며, `why` 의 질문도 "왜 이런 인연일까요?"다. 그래서 세 자리 모두 오행 쌍(15)을 쓴다. **방향이 있는 것은 소개팅(C) 하나**다(카드는 보는 사람 혼자 본다) → 방향 관계 5 또는 순서쌍 25
- 궁합 쪽 구간은 3등분이 아니라 **인연 유형 4**를 쓴다. 화면에 유형 이름이 나오므로 구간이 유형 경계를 넘으면 "스침" 행에 "귀인" 톤의 글이 붙는다
- 운명 설명은 등급이 없어서 구간 자리에 **운명 유형 8종**(`DestinyTitle`, 상/하 2×2×2)을 쓴다. 잘 맞는 오행은 등급이 없어 구간 문장이 없다

### 수준 정의

- **최소**: 키 하나 × 구간. 성별을 키로 안 쓰므로 배우자·자녀 문장은 성별 중립으로 써야 한다
- **추천**: 본문(키 조합) + 구간 문장을 이어 붙인다 → `본문 수 + 구간 수`. 실제로 나오는 서로 다른 글은 `본문 × 구간`
- **풍부**: 키를 모두 곱해 통째로 쓴다. 궁합·소개팅 자리는 변형 2개를 곱한다 (변형 3개면 해당 자리 × 1.5)

### 자리별 표

| 자리 | 최소 | 추천 (본문 + 구간) | 풍부 |
|---|---|---|---|
| A `destinyDescription` | 일간 10 → **10** | (일간 10 × 계절 4) + 운명 유형 8 = `40 + 8` = **48** | `40 × 8` = **320** |
| A `love` | 많은 기운 역할 5 × 구간 3 = **15** | (많은 기운 역할 5 × 성별 2) + 구간 3 = `10 + 3` = **13** | `10 × 3` = **30** |
| A `marriage` | 일지 역할 5 × 구간 3 = **15** | (일지 역할 5 × 성별 2) + 구간 3 = `10 + 3` = **13** | `10 × 3` = **30** |
| A `children` | 자녀 기운 세기 3 × 구간 3 = **9** | (자녀 기운 세기 3 × 성별 2) + 구간 3 = `6 + 3` = **9** | `6 × 3` = **18** |
| A `elementMatch` | 보완 오행 5 → **5** | 보완 오행 5 × 그 뜻 5 = `25 + 0` = **25** | **25** |
| **A 소계** | `10+15+15+9+5` = **54** | `48+13+13+9+25` = **108** | `320+30+30+18+25` = **423** |
| B `why` | 대칭 관계 3 × 유형 4 = **12** | 오행 쌍 15 + 유형 4 = `15 + 4` = **19** | 오행 쌍 15 × 일지 6 × 유형 4 × 변형 2 = **720** |
| B `together` | 대칭 관계 3 × 유형 4 = **12** | 오행 쌍 15 + 유형 4 = `15 + 4` = **19** | `15 × 6 × 4 × 2` = **720** |
| B `conflict` | 대칭 관계 3 × 유형 4 = **12** | (일지 6 × 대칭 관계 3) + 유형 4 = `18 + 4` = **22** | `15 × 6 × 4 × 2` = **720** |
| **B 소계** | `12 × 3` = **36** | `19+19+22` = **60** | `720 × 3` = **2,160** |
| C `reason` | 방향 관계 5 × 유형 4 = **20** | 순서쌍 25 + 유형 4 = `25 + 4` = **29** | 순서쌍 25 × 일지 6 × 유형 4 × 변형 2 = **1,200** |
| **합계** | `54 + 36 + 20` = **110** | `108 + 60 + 29` = **197** | `423 + 2,160 + 1,200` = **3,783** |

- 대칭 관계 3 = 같음·상생·상극. 누가 누구를 살리는지는 `{elementA}`·`{elementB}` 자리표시자로 채운다
- C 추천의 5문장 구조(장면 → 까닭 → 상대 성향 → 유형 핵심 → 마무리)는 앞 3문장 = 본문, 뒤 2문장 = 유형 구간으로 자연스럽게 나뉜다
- 최소 수준의 `love` 15 가 추천 13 보다 많은 것은 오기가 아니다. 추천은 곱하지 않고 더하므로 적게 써도 나오는 글은 `10 × 3 = 30` 가지다

---

## 5. 수준별 비교 (3단계)

| | 최소 | 추천 | 풍부 |
|---|---|---|---|
| 총 템플릿 | 110 | 197 | 3,783 |
| 생성 호출 (10개씩) | `⌈110/10⌉` = 11 | `⌈197/10⌉` = 20 | `⌈3,783/10⌉` = 379 |
| 검수 (개당 15초) | `110 × 15` = 1,650초 = **27분 30초** | `197 × 15` = 2,955초 = **49분 15초** | `3,783 × 15` = 56,745초 = **15시간 45분 45초** |
| 한 사람의 사주 결과 조합 수 | `10 × 15 × 15 × 9 × 5` = **101,250** | `(40×8) × (10×3) × (10×3) × (6×3) × 25` = `320 × 30 × 30 × 18 × 25` = **129,600,000** | 추천과 같음 **129,600,000** (같은 키 조합을 통째로 쓴다. 이득은 개수가 아니라 문장 흐름) |
| 한 궁합의 이유 조합 수 (B 세 자리) | 세 자리가 같은 키 → **12** | 오행 쌍 15 × 일지 6 × 유형 4 = **360** | `360 × 2³` = **2,880** (자리마다 변형을 따로 고를 때) |
| 소개팅 카드 조합 수 (C) | **20** | `25 × 4` = **100** | **1,200** |
| 친구 3 / 5 / 10명, B 세 자리 중 하나라도 **글 전체가 같을** 확률 | 25.2% / 64.7% / 99.7% | 16.2% / 45.9% / 95.3% | 2.3% / 7.1% / 28.4% |
| 〃, 추천의 **본문 문장만** 같을 확률 | — | 57.0% / 97.4% / 100% | — |
| 참고: `why` 한 자리만 | 25.3% / 64.7% / 99.7% | 14.5% / 41.9% / 93.5% | 1.2% / 4.1% / 17.3% |
| 참고: 소개팅 Top 3 카드 중 겹침 | 14.5% | 14.5% (본문만 52.0%) | 1.2% |

**가정**
- 궁합지도 주인의 팔자는 고정이고, 친구의 키 값은 **서로 독립이며 균등**하다: 친구 일간 오행 5가지 각 1/5, 일지 관계 6가지 각 1/6, 인연 유형 4가지 각 1/4, 변형 균등
- 주인 오행이 고정이라 오행 쌍은 15가 아니라 **친구 오행 5가지**로 줄어든다. 여기서 대칭 관계는 같음 1/5 · 상생 2/5 · 상극 2/5 로 유도된다 (균등 아님)
- 실제 분포는 균등하지 않다. 일지 관계는 "없음"이 44.4%(§3)이고 유형도 균등하지 않을 것이라 **실제 겹침 확률은 위 값보다 높다**
- 조합 수는 키가 독립이라 가정한 **상한**이다. 일간과 일지는 음양이 묶여 있어(일주 60 = `10 × 12 / 2`) 실제로는 더 적다

**계산식**
- 한 자리 겹침: 칸별 확률 `p_i`, 친구 N명일 때 `1 − N! × e_N(p)` (`e_N` = N차 기본대칭다항식). 균등 K칸이면 `1 − ∏_{i=0}^{N−1} (1 − i/K)`
  - 예: 추천 `why` 는 친구 오행 5 × 유형 4 = 20칸 균등 → N=3 에서 `1 − (19/20)(18/20)` = `1 − 0.855` = 14.5%
  - 예: 추천 본문은 5칸 → N=3 에서 `1 − (4/5)(3/5)` = 52.0%, N=10 은 칸(5)보다 사람이 많아 100%
- 세 자리 중 하나라도: 자리끼리 키를 공유해 독립이 아니므로 20만 회 모의실험 값 (시드 고정)

---

## 6. 점수 구간 경계 산출용 SQL (4단계)

### 코드상 범위

| 점수 | 범위 | 계산 |
|---|---|---|
| 결혼·자녀·연애 | 0~100 정수 | `ReadingScorer.raw()` 원점수 → `calibrate()`: `74 + (raw − 중앙값) × 배율` (결혼 63.0·0.93 / 자녀 51.9·0.78 / 연애 54.1·0.95). 90 초과·10 미만은 지수로 눌러 90~100·0~10 에 펼친다. 설계상 중앙값 74(= A+ 컷), 표준편차 약 14 |
| 친구 궁합·소개팅 | 0~100 정수 | `CompatibilityCalculator.calculate()`: 50 에서 기둥별 천간·지지 관계 가감 + 오행 균형(−10~10) + 음양(−5~5) → 0~100 으로 자름 → 구간별 선형 보정(원점수 50/60/70 → 61/75/90) |

### SQL (집계 전용, 직접 실행용)

출력은 개수와 경계값뿐이다. 닉네임·생년월일·시간·연락처 컬럼은 SELECT 에 없다.
재제출(plan 2.8, 16.7%)로 같은 사람이 여러 행을 만들므로 카테고리 점수는 **같은 팔자·성별을 한 번만** 센다. 팔자 컬럼은 중복 제거에만 쓰고 출력하지 않는다.

```sql
-- 1) 결혼·자녀·연애 점수의 33.3%·66.7% 지점
--    rd.version = 0 은 #62(V8) 이전 행. 점수 로직이 바뀌었을 수 있어 뺀다. 넣고 비교해 봐도 된다
WITH uniq AS (
    SELECT DISTINCT ON (r.year_pillar, r.month_pillar, r.day_pillar, r.hour_pillar, r.gender)
           rd.marriage_score, rd.children_score, rd.love_score
    FROM reading rd
    JOIN result r ON r.id = rd.result_id
    WHERE rd.version <> 0
    ORDER BY r.year_pillar, r.month_pillar, r.day_pillar, r.hour_pillar, r.gender, rd.created_at
)
SELECT 'MARRIAGE' AS category, count(*) AS n,
       percentile_cont(ARRAY[0.333, 0.667]) WITHIN GROUP (ORDER BY marriage_score::float8) AS cuts
FROM uniq
UNION ALL
SELECT 'CHILDREN', count(*),
       percentile_cont(ARRAY[0.333, 0.667]) WITHIN GROUP (ORDER BY children_score::float8)
FROM uniq
UNION ALL
SELECT 'LOVE', count(*),
       percentile_cont(ARRAY[0.333, 0.667]) WITHIN GROUP (ORDER BY love_score::float8)
FROM uniq;

-- 2) 친구 궁합 점수의 33.3%·66.7% 지점과 유형별 개수 (궁합 구간은 유형 4를 쓰자는 §4 제안의 근거 확인용)
SELECT 'COMPATIBILITY' AS kind, count(*) AS n,
       percentile_cont(ARRAY[0.333, 0.667]) WITHIN GROUP (ORDER BY score::float8) AS cuts,
       count(*) FILTER (WHERE tier = 'GUIIN')     AS guiin,
       count(*) FILTER (WHERE tier = 'CHALTTEOK') AS chaltteok,
       count(*) FILTER (WHERE tier = 'BEOT')      AS beot,
       count(*) FILTER (WHERE tier = 'SEUCHIM')   AS seuchim
FROM compatibility
UNION ALL
-- 3) 소개팅 추천 점수 (같은 계산기)
SELECT 'DATING', count(*),
       percentile_cont(ARRAY[0.333, 0.667]) WITHIN GROUP (ORDER BY score::float8),
       count(*) FILTER (WHERE score >= 90),
       count(*) FILTER (WHERE score BETWEEN 75 AND 89),
       count(*) FILTER (WHERE score BETWEEN 61 AND 74),
       count(*) FILTER (WHERE score <= 60)
FROM dating_recommendation;
```

---

## 7. 저장 구조 초안 (5단계, 구현하지 않음)

```sql
-- 번호는 handoff.md 예약 표에 먼저 적은 뒤 정한다
CREATE TABLE fallback_template (
    id            BIGSERIAL    PRIMARY KEY,
    slot          VARCHAR(30)  NOT NULL,  -- DESTINY, LOVE, MARRIAGE, CHILDREN, ELEMENT_MATCH, COMPAT_WHY, COMPAT_TOGETHER, COMPAT_CONFLICT, DATING_REASON
    template_key  VARCHAR(50)  NOT NULL,  -- 예: 'GAP|WINTER', 'ROLE2|MALE', 'WOOD|FIRE'. 구간 문장 행은 '-'
    band          VARCHAR(20)  NOT NULL,  -- 예: 'LOW'/'MID'/'HIGH', 'GUIIN'…, 'TYPE_1'…'TYPE_8'. 본문 행은 '-'
    variant       SMALLINT     NOT NULL DEFAULT 0,
    text          TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (slot, template_key, band, variant)
);
```

- `key` 는 SQL 예약어와 겹쳐 `template_key` 로 했다. "해당 없음"을 NULL 이 아닌 `'-'` 로 둔 것은 NULL 끼리는 UNIQUE 에서 겹치지 않기 때문이다 (PG16 이라 `UNIQUE NULLS NOT DISTINCT` 도 가능)
- 추천 수준은 본문 행(`band = '-'`)과 구간 행(`template_key = '-'`)을 하나씩 골라 이어 붙인다. 최소·풍부는 두 값이 다 찬 행 하나를 쓴다

**자리표시자**
- 지금 LLM 글에는 닉네임이 들어가지 않는다(프롬프트가 닉네임을 모르고, 궁합 글은 누구도 호칭하지 않는다). 그래서 `{nickname}` 은 현재 자리에 필요 없다
- 필요한 것은 오행 이름이다: `{myElement}`, `{strongElement}`, `{spouseElement}`, `{spouseSeatElement}`, `{childElement}`, `{matchElement}`, `{elementA}`·`{elementB}`(상생·상극의 주체·대상), `{partnerElement}`(소개팅 상대). 값은 `ReadingGenerator.PLAIN` (나무·불·흙·쇠·물)
- 템플릿은 항상 "`{x}`의 기운" 꼴로 쓴다. 뒤에 붙는 조사가 늘 "기운" 에 붙어서 받침 처리(`은/는`)가 필요 없다

**고르는 규칙** — 새로고침해도 같은 글
- `variant = pick(slot + ":" + id, variant 수)`. 구현은 `LuckyPool.pick()` 처럼 `new SplittableRandom(key.hashCode()).nextInt(n)` 을 쓴다. `String.hashCode()` 만 쓰면 끝 글자에 선형이라 순번 id(`compatibility.id`)가 variant 를 차례로 돈다
- id: 사주 = `resultId`, 친구 궁합 = `compatibility.id`, 소개팅 보낸 사람 = `dating_recommendation.id`, 받은 사람 = `dating_request.id`
- `slot` 을 해시 키에 넣어 세 자리의 variant 가 서로 따로 움직이게 한다 (§5 풍부 조합 `2³` 의 전제)

---

## 8. 확인이 필요한 것

**문서·요청서와 코드가 다른 부분**
1. `docs/v1-spec.md` 가 저장소에 없다. 같은 번호(1.2, 3.5, 3.7, 3.8, 5.9, 5.10)가 있는 `docs/plan.md` 를 기준으로 했다. 패키지별 `AGENTS.md` 도 없다(`common/agents.md` 뿐)
2. 행운 아이템·장소는 LLM 이 아니다(풀 파일). 반대로 운명 카드 설명(`destinyDescription`)과 소개팅 궁합 까닭(`reason`)은 LLM 인데 요청서 목록에 없었다
3. 인연 유형 해설(5.9)은 백엔드에 필드가 없다. 프론트 고정 문구인지 확인 필요
4. plan 5.10 은 "왜 **나에게** 귀인일까요?"(방향 있음)지만, 코드는 한 행을 두 사람이 같이 보는 **대칭** 글이고 프롬프트 질문도 "왜 이런 인연일까요?"다. 방향 있는 글로 바꾸려면 저장 컬럼이 두 벌 필요하다(스키마 변경). 문구도 "싸움이 난다면"(plan)과 "싸우게 된다면"(프롬프트)으로 다르다
5. **TBD-2(궁합 이유 방식 재검토)가 아직 열려 있다.** 이 분석은 괜찮지만 B 자리에 템플릿을 붙이는 구현은 TBD-2 결정 뒤에 해야 한다

**키 계산이 불가능하거나 규칙이 필요한 부분**
6. 일지 관계는 계산기 안에 `private` 로만 있고, **형(刑)은 없다.** `인해`·`사신` 은 합이면서 파다. §3 의 6가지 안(합 > 충 > 삼합 > 같음 > 해·파 > 없음)으로 갈지 정해야 한다
7. 가장 강한/약한 오행은 동점·"없음"이 생긴다. 동점 규칙(§3)이 정해져야 키가 하나로 떨어진다
8. 자녀 기운·배우자 기운은 `ReadingGenerator.buildPrompt()` 안의 지역변수라 꺼낼 메서드가 필요하다. `saju/`(차은호)·`compatibility/`(최선우) 담당 수정이다
9. 카테고리 구간을 새로 3등분할지, 이미 있는 상/하(A+ 컷 74, 설계상 중앙값)를 쓸지. 3등분이면 §6 SQL 결과가 필요하다

**운영상 확인**
10. 사주 결과는 LLM 실패 시 **결과 생성 자체가 실패**한다(동기 호출). 템플릿이 가장 급한 곳은 여기다. 궁합·소개팅은 지연 생성이라 실패해도 그 영역만 빠진다
11. 템플릿 글을 `reading` 에 저장하면 같은 입력의 다음 결과가 #62 재사용으로 템플릿 글을 계속 물려받는다. 템플릿 행을 구별하는 표시(예: 별도 `version` 값)가 필요한지 결정 필요. 궁합·소개팅 캐시 컬럼도 같은 문제(한 번 템플릿이 저장되면 LLM 글로 다시 바뀌지 않음)
12. 지금 `GeminiJson` 은 **Google 503일 때만** 유료로 1회 전환하고 429는 전환하지 않는다. "무료 실패 → 유료" 계획과 범위가 다르다
13. 최소 수준은 성별을 키로 안 쓰므로 배우자·자녀 문장을 성별 중립으로 써야 한다. 프롬프트 규칙("역할어는 성별에 맞춘다")과 맞는지 검수 기준에 넣어야 한다
14. 저장 위치: 이 저장소는 고정 문구를 이미 리소스 파일로 둔다(`lucky/items.txt`, `lucky/places.txt`, `destiny-titles.txt`). 리소스 파일이면 Flyway 번호 예약·마이그레이션 없이 PR 리뷰로 검수할 수 있다. DB 테이블(§7)과 둘 중 하나를 골라야 한다

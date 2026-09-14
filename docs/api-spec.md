# api-spec.md

**프론트 팀(3명)과의 계약서.** 백엔드가 관리하고 백엔드가 책임진다.

1. **문서가 먼저 바뀌고 코드가 따라간다.** 문서 없이 만든 엔드포인트는 없는 것으로 취급
2. **필드 삭제·타입 변경은 사전 합의.** 말없이 바꾸면 프론트 3명이 동시에 막힌다. 추가는 자유
3. 변경 시 `docs/handoff.md` 의 "프론트에 공지한 API 변경" 표에 기록

Base URL: `/api` · Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`
(Swagger는 참고용이고 **원본은 이 문서다**)

---

## 1. 공통 응답 포맷

```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{
  "success": false,
  "error": { "code": "RESULT_NOT_FOUND", "message": "결과를 찾을 수 없습니다.", "traceId": "a1b2c3" }
}
```

- HTTP 상태코드도 함께 맞춘다 (200/201/302/400/404/409/500/503)
- `error.message` 는 **사용자에게 그대로 보여줄 수 있는 한국어**
- 스택트레이스·SQL 오류를 `message` 에 절대 넣지 않는다
- `traceId` 는 장애 문의 시 로그 추적용. 프론트가 화면에 노출해도 된다

### 에러 코드

`common/exception/ErrorCode.java` 와 **1:1 대응**한다.

| code | HTTP | 상황 |
|---|---|---|
| `INVALID_INPUT` | 400 | 유효성 검증 실패 |
| `RESULT_NOT_FOUND` | 404 | resultId 없음 |
| `SELF_COMPATIBILITY` | 400 | 자기 자신과 궁합 요청 |
| `DUPLICATE_SIGNUP` | 409 | 이미 신청한 이메일 |
| `INVALID_EMAIL_DOMAIN` | 400 | 학교 웹메일 아님 |
| `INVALID_TOKEN` | 400 | 인증 토큰 만료·위조·재사용 |
| `LLM_UNAVAILABLE` | 503 | 해석 생성 실패 |
| `INTERNAL_ERROR` | 500 | 그 외 |
| `NOT_FOUND` | 404 | 존재하지 않는 경로 |

---

## 2. 사주 생성

### `POST /api/results`

계산 + 해석 생성. **3~10초 걸릴 수 있다.** 프론트는 로딩 UX 필수.

**Request**

```json
{
  "nickname": "도윤",
  "calendarType": "SOLAR",
  "birthDate": "2002-03-14",
  "isLeapMonth": false,
  "birthTime": "14:30",
  "gender": "MALE"
}
```

| 필드 | 검증 |
|---|---|
| `nickname` | 1~8자, 공백만 불가, 필수 |
| `calendarType` | `SOLAR` \| `LUNAR`, 필수 |
| `birthDate` | `yyyy-MM-dd`, 1950-01-01 ~ 오늘, 필수. `LUNAR` 면 음력 날짜 |
| `isLeapMonth` | boolean. `LUNAR` 이고 윤달이면 `true`. 생략 시 `false`. `SOLAR` 면 무시 |
| `birthTime` | `HH:mm` 또는 **null(모름)** |
| `gender` | `MALE` \| `FEMALE`, 필수. 결혼·자녀 점수(배우자성·자녀성)와 해석 문장의 역할 표현에 쓴다 |

**음력 입력**

- 서버가 한국 음력(한국천문연구원 기준)으로 양력 변환 후 계산·저장한다. 응답과 저장값은 항상 양력
- 존재하지 않는 음력 날짜(그 달에 없는 30일, 그 해에 없는 윤달) → 400 `INVALID_INPUT`

**시간 입력 (프론트 시진 선택 UI 기준)**

- 시진(2시간 단위)을 고르면 그 칸의 **가운데 시각**을 보낸다. 예: 묘시 05:30~07:30 → `"06:30"`
- **자시는 두 칸으로 나눈다**: `자시 00:00~01:30` → `"00:45"`, `자시 23:30~24:00` → `"23:45"`.
  자정을 걸치는 칸이라 날짜만으로는 새벽/밤 구분이 안 되고, 둘은 사주가 다르다
- 출생 지역은 받지 않는다 (9/13 기획 결정). 시진 단위 입력이라 지역 시차 보정이 결과에 영향이 없다. 서버는 서울 경도 기준으로 계산한다

**Response 201**

```json
{
  "success": true,
  "data": {
    "resultId": "3f2a9c1e-....",
    "shareId": "7b91d26f-....",
    "nickname": "도윤",
    "zodiac": "HORSE",
    "destiny": {
      "title": "깔깔깔깔깔깔깔깔깔",
      "description": "당신은 특별한 운명을 타고났습니다. 앞으로 좋은 흐름을 맞이하게 됩니다."
    },
    "fortunes": [
      { "category": "MARRIAGE", "grade": "SS", "content": "결혼운에 대한 설명" },
      { "category": "CHILDREN", "grade": "A+", "content": "자녀운에 대한 설명" },
      { "category": "LOVE",     "grade": "B",  "content": "연애운에 대한 설명" }
    ],
    "luckyItem": "파란색 팔찌",
    "luckyPlace": "야외 무대",
    "compatibilities": []
  }
}
```

- 화면의 고정 문구인 “당신의 운명은”은 프론트에서 표시한다
- `fortunes` 순서는 `MARRIAGE` → `CHILDREN` → `LOVE`로 고정한다
- `zodiac` 은 십이간지 띠. `RAT` `OX` `TIGER` `RABBIT` `DRAGON` `SNAKE` `HORSE` `GOAT` `MONKEY` `ROOSTER` `DOG` `PIG`.
  **입춘 기준**이라 양력 연도로 계산한 띠와 1~2월생에서 다를 수 있다. 프론트가 생년으로 직접 계산하지 않는다. 캐릭터 이름·이모지는 프론트 매핑
- `grade` 는 6단계 고정: `SS` 94~100 · `S` 84~93 · `A+` 74~83 · `A` 64~73 · `B+` 52~63 · `B` 0~51 (점수 기준, 2026-09-13 기획 확정)
- `destiny.title` 은 8종 고정: 연애·결혼·자녀 각각 상(`SS`/`S`/`A+`)·하(`A`/`B+`/`B`) 조합 2×2×2. 유형 번호는 기능명세서 순서(1 상상상 … 8 하하하, 연애→결혼→자녀). 제목 문구는 기획(영채) 확정 전 임시값. 점수로 계산하므로 저장하지 않는다
- `luckyItem` 은 **오늘의 행운 아이템**. 기능명세서 방식: 오행별 점수 = 사용자 궁합(일간 기준 십성, 인성>비겁>식상>재성>관성) 40% + 오늘 일진 활성도(일진 천간·지지 오행과 생극) 60%. 최고 오행의 풀에서 `resultId + 날짜 + 오행` 해시로 하나. **매일 바뀌고** 같은 팔자라도 사람마다 다르다. 저장하지 않고 조회 시점에 계산하므로 `POST` 응답과 다음 날 `GET` 응답이 다를 수 있다
- `luckyPlace` 는 **행운의 장소**. 원국 오행 세력으로 일간 강약을 보고 균형을 보완하는 오행을 정해 그 오행의 장소 풀에서 팔자 해시로 하나. **사람마다 고정**, 날짜 무관. 동국대 캠퍼스 안
- 사주 팔자는 저장하지만 API 응답에는 노출하지 않는다
- 본인 결과 조회에는 `resultId`, 친구 공유 URL에는 `shareId`를 사용한다

---

## 3. 결과 조회

### `GET /api/results/{resultId}`

본인 결과 재방문. **LLM 재호출 없이 DB에서 반환.**
가장 트래픽이 몰리는 엔드포인트다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "resultId": "3f2a9c1e-....",
    "shareId": "7b91d26f-....",
    "nickname": "도윤",
    "zodiac": "HORSE",
    "destiny": { "title": "깔깔깔깔깔깔깔깔깔", "description": "..." },
    "fortunes": [ ... ],
    "luckyItem": "파란색 팔찌",
    "luckyPlace": "야외 무대",
    "compatibilities": [
      { "nickname": "민수", "score": 31, "tier": "SEUCHIM",  "createdAt": "2026-09-11T13:20:00Z" },
      { "nickname": "지현", "score": 92, "tier": "GUIIN", "createdAt": "2026-09-11T12:04:00Z" }
    ]
  }
}
```

- `compatibilities` 는 `createdAt` 내림차순
- **상대의 생년월일·성별·resultId 는 내려보내지 않는다.** 닉네임과 점수만

### `GET /api/shares/{shareId}`

친구가 공유 링크로 진입할 때 링크 주인의 공개 결과와 궁합 지도를 조회한다.
응답 구조는 결과 조회와 같지만 내부 식별자인 `resultId`와 공개 키인 `shareId`는 포함하지 않는다.

---

## 4. 친구 궁합

### `POST /api/shares/{shareId}/compatibility`

친구가 공유 링크에서 자기 정보를 `POST /api/results`로 입력한 직후 호출한다.
`{shareId}` = 링크 주인의 공개 ID, `guestResultId` = 친구가 방금 생성한 결과 ID.

**Request**

```json
{ "guestResultId": "3f2a9c1e-...." }
```

**Response 201**

```json
{
  "success": true,
  "data": {
    "score": 92,
    "tier": "GUIIN",
    "originNickname": "도윤",
    "guestNickname": "지현"
  }
}
```

- `shareId`의 링크 주인 결과와 `guestResultId`가 같으면 `SELF_COMPATIBILITY` 400
- 이미 있는 조합이면 기존 값을 그대로 **200**으로 반환. 재계산하지 않는다
- `score(A,B) == score(B,A)` 보장
- `tier` 구간 (2026-09-13 기획 확정, 25점 구간 아님): `GUIIN` 90~100 · `CHALTTEOK` 75~89 · `BEOT` 61~74 · `SEUCHIM` 0~60

---

## 5. 소개팅 사전등록

### `POST /api/signups`

**Request**

```json
{
  "email": "dev@dgu.ac.kr",
  "resultId": "3f2a9c1e-....",
  "gender": "MALE",
  "preferGender": "FEMALE"
}
```

- `resultId` **nullable** — 사주 없이 신청하는 경로 허용
- 도메인 화이트리스트 위반 → `INVALID_EMAIL_DOMAIN` 400
- 중복 이메일 → `DUPLICATE_SIGNUP` 409
- **이름·전화번호는 받지 않는다**

**Response 201**

```json
{
  "success": true,
  "data": {
    "signupId": 1024,
    "couponIssued": true,
    "mailSent": true,
    "message": "신청이 접수됐다. 인증 메일을 확인해라."
  }
}
```

`mailSent: false` 여도 신청은 성공이다. SMTP 실패가 신청을 롤백시키지 않는다.
프론트는 이 경우 재발송 안내를 노출한다.

### `POST /api/signups/resend`

**Request**

```json
{ "email": "dev@dgu.ac.kr" }
```

메일 재발송. 이미 인증 완료된 이메일이면 400 `INVALID_INPUT` (전용 에러코드 없음).
신청 내역이 없는 이메일도 400 `INVALID_INPUT`.

**Response 200**

```json
{
  "success": true,
  "data": {
    "mailSent": true,
    "message": "인증 메일을 재발송했다."
  }
}
```

`mailSent: false` 여도 200이다. SMTP 실패가 요청을 실패시키지 않는다 (§2 `POST /api/results`와 동일 원칙).

### `GET /api/signups/verify?token={token}`

매직링크 클릭. 성공 시 **프론트 완료 페이지로 302 리다이렉트**.
리다이렉트 대상은 설정값(`app.frontend.verify-redirect-url`).

토큰 유효기간 30분, 사용 후 재사용 불가.

---

## 6. 헬스체크

### `GET /api/health`

```json
{ "success": true, "data": { "status": "UP" } }
```

---

## 7. 직렬화 규칙

- JSON 키는 **camelCase**
- 날짜 `yyyy-MM-dd`, 시각은 ISO-8601 **UTC(`Z`)**
- **null 필드도 키를 유지한다.** 프론트가 `undefined` 체크를 안 해도 되게
- 리소스명은 복수형 명사 (`/results`, `/signups`). 동사 금지

---

## 8. 프론트 팀 전달 일정

프론트 3명은 백엔드를 기다리지 않고 목 데이터로 시작한다.
**Day 1에 이 문서를 확정해 넘기는 게 최우선이다.** 코드보다 먼저다.

| 시점 | 넘기는 것 |
|---|---|
| Day 1 | 이 문서 확정본 + CORS에 `localhost:3000` 허용 |
| Day 2 | `POST /api/results` 동작 (해석은 더미여도 됨) |
| Day 3 | 해석 실제 생성 + `GET /api/results/{id}` |
| Day 4 | 궁합 API |
| Day 5 | 사전등록 API |

목 데이터와 실제 응답이 어긋나면 **이 문서를 보고 잘못된 쪽을 고친다.**
"백엔드가 이미 그렇게 짰으니까"는 근거가 아니다.

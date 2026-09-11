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
  "birthDate": "2002-03-14",
  "birthTime": "14:30",
  "birthRegion": "서울",
  "gender": "MALE"
}
```

| 필드 | 검증 |
|---|---|
| `nickname` | 1~20자, 공백만 불가, 필수 |
| `birthDate` | `yyyy-MM-dd`, 1950-01-01 ~ 오늘, 필수 |
| `birthTime` | `HH:mm` 또는 **null(모름)** |
| `birthRegion` | 최대 50자 또는 **null(모름)** |
| `gender` | `MALE` \| `FEMALE`, 필수 |

**Response 201**

```json
{
  "success": true,
  "data": {
    "resultId": "3f2a9c1e-....",
    "nickname": "도윤",
    "pillars": { "year": "임오", "month": "계묘", "day": "갑진", "hour": "신미" },
    "hourUnknown": false,
    "readings": [
      { "category": "LOVE",          "score": 78, "content": "..." },
      { "category": "WEALTH",        "score": 45, "content": "..." },
      { "category": "STUDY",         "score": 62, "content": "..." },
      { "category": "FESTIVAL_ITEM", "score": 88, "content": "..." },
      { "category": "FORTUNE",       "score": 55, "content": "..." }
    ]
  }
}
```

- `hourUnknown: true` 면 `pillars.hour` 는 `null` (키는 유지한다)
- `readings` 순서는 **항상 위 순서로 고정.** 프론트가 정렬하지 않아도 되게
- 공유 URL은 프론트가 조립한다. 백엔드는 `resultId` 만 준다

---

## 3. 결과 조회

### `GET /api/results/{resultId}`

재방문·공유 링크 진입. **LLM 재호출 없이 DB에서 반환.**
가장 트래픽이 몰리는 엔드포인트다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "resultId": "3f2a9c1e-....",
    "nickname": "도윤",
    "pillars": { "year": "임오", "month": "계묘", "day": "갑진", "hour": "신미" },
    "hourUnknown": false,
    "readings": [ ... ],
    "compatibilities": [
      { "nickname": "지현", "score": 82, "tier": "GUIIN", "createdAt": "2026-09-11T12:04:00Z" },
      { "nickname": "민수", "score": 31, "tier": "BEOT",  "createdAt": "2026-09-11T13:20:00Z" }
    ]
  }
}
```

- `compatibilities` 는 `createdAt` 내림차순
- **상대의 생년월일·성별·resultId 는 내려보내지 않는다.** 닉네임과 점수만

---

## 4. 친구 궁합

### `POST /api/results/{resultId}/compatibility`

친구가 `?ref={originId}` 로 들어와 자기 사주를 본 직후 호출.
`{resultId}` = 친구 본인의 결과, `originId` = 링크 주인.

**Request**

```json
{ "originId": "3f2a9c1e-...." }
```

**Response 201**

```json
{
  "success": true,
  "data": {
    "score": 82,
    "tier": "GUIIN",
    "originNickname": "도윤",
    "guestNickname": "지현"
  }
}
```

- `originId == resultId` → `SELF_COMPATIBILITY` 400
- 이미 있는 조합이면 기존 값을 그대로 **200**으로 반환. 재계산하지 않는다
- `score(A,B) == score(B,A)` 보장

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

```json
{ "email": "dev@dgu.ac.kr" }
```

메일 재발송. 이미 인증 완료된 이메일이면 400.

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
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

- HTTP 상태코드도 함께 맞춘다 (200/201/302/400/401/404/409/500/503)
- 인증이 필요한 API 는 로그인 쿠키(`wks_token`, HttpOnly)로 인증한다 (§9). `credentials: 'include'` 로 호출해야 한다. 사주·궁합·공유 API 는 인증이 없다
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
| `COMPATIBILITY_NOT_FOUND` | 404 | 궁합 `id` 없음 |
| `DUPLICATE_SIGNUP` | 409 | 이미 신청한 이메일 |
| `INVALID_EMAIL_DOMAIN` | 400 | 학교 웹메일 아님 |
| `INVALID_TOKEN` | 400 | 인증 토큰 만료·위조·재사용 |
| `LLM_UNAVAILABLE` | 503 | 해석 생성 실패. Gemini 오류·타임아웃, 또는 서버 호출 총량 상한(분당 60·일 1,600) 초과. 잠시 후 재시도 안내 |
| `UNAUTHENTICATED` | 401 | 토큰 없음·만료·위조 |
| `KAKAO_UNAVAILABLE` | 503 | 카카오 서버 오류·타임아웃 |
| `DATING_PROFILE_NOT_FOUND` | 404 | 내 소개팅 프로필 없음 |
| `DATING_PROFILE_CONFLICT` | 409 | 프로필 또는 학교 이메일 중복 |
| `DATING_NOT_VERIFIED` | 403 | 학교 이메일 인증 전 후보 조회 |
| `DATING_REQUEST_NOT_FOUND` | 404 | 요청 없음 또는 받은 사람 본인이 아님 |
| `DATING_REQUEST_CONFLICT` | 409 | 중복 요청, 대상 미노출, 이미 처리된 요청 |
| `METHOD_NOT_ALLOWED` | 405 | 존재하는 경로에 지원하지 않는 HTTP 메서드 사용 |
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
      "title": "오래 사랑할 운명",
      "description": "당신은 특별한 운명을 타고났습니다. 앞으로 좋은 흐름을 맞이하게 됩니다."
    },
    "fortunes": [
      { "category": "MARRIAGE", "grade": "SS", "content": "결혼운에 대한 설명" },
      { "category": "CHILDREN", "grade": "A+", "content": "자녀운에 대한 설명" },
      { "category": "LOVE",     "grade": "B",  "content": "연애운에 대한 설명" }
    ],
    "elements": { "wood": 3, "fire": 2, "earth": 1, "metal": 1, "water": 1 },
    "elementMatch": { "element": "EARTH", "korean": "토", "reason": "흙의 기운은 당신을 살려 주는 기운이에요. ..." },
    "luckyItem": "파란색 팔찌",
    "luckyPlace": "야외 무대",
    "compatibilities": []
  }
}
```

- 화면의 고정 문구인 “당신의 운명은”은 프론트에서 표시한다
- `elementMatch` 는 **나와 잘 맞는 오행 + 이유** (기능명세 3.5). `element` 는 `WOOD` `FIRE` `EARTH` `METAL` `WATER`, `korean` 은 목·화·토·금·수. 한자(木 등)는 프론트 매핑. 오행은 `luckyPlace` 와 같은 보완 오행이라 사람마다 고정, `reason` 은 2~3문장. CTA "OO 기운의 사람 만나보기"(3.6)는 `element` 를 그대로 쓴다. **`null` 이면 영역을 그리지 않는다** (이 필드가 생기기 전에 만든 결과)
- `fortunes` 순서는 `MARRIAGE` → `CHILDREN` → `LOVE`로 고정한다
- `zodiac` 은 십이간지 띠. `RAT` `OX` `TIGER` `RABBIT` `DRAGON` `SNAKE` `HORSE` `GOAT` `MONKEY` `ROOSTER` `DOG` `PIG`.
  **입춘 기준**이라 양력 연도로 계산한 띠와 1~2월생에서 다를 수 있다. 프론트가 생년으로 직접 계산하지 않는다. 캐릭터 이름·이모지는 프론트 매핑
- `grade` 는 6단계 고정: `SS` 94~100 · `S` 84~93 · `A+` 74~83 · `A` 64~73 · `B+` 52~63 · `B` 0~51 (점수 기준, 2026-09-13 기획 확정)
- `elements` 는 사주 원국 각 글자의 오행 개수다. 출생 시간이 있으면 합계 8, 모르면 시주를 제외해 합계 6이다. **해설 문장이 말하는 "많은 기운"도 이 개수 기준**이라 화면과 어긋나지 않는다 (점수·행운 장소는 자리 가중치를 쓰지만 응답에 노출되지 않는다)
- `destiny.title` 은 8종 고정: 연애·결혼·자녀 각각 상(`SS`/`S`/`A+`)·하(`A`/`B+`/`B`) 조합 2×2×2. 유형 번호는 기능명세서 순서(1 상상상 … 8 하하하, 연애→결혼→자녀). 제목 문구는 기획(영채) 피드백 1차 (2026-09-15) 반영. 점수로 계산하므로 저장하지 않는다
- `luckyItem` 은 **오늘의 행운 아이템**. 기능명세서 방식: 오행별 점수 = 사용자 궁합(일간 기준 십성, 인성>비겁>식상>재성>관성) 40% + 오늘 일진 활성도(일진 천간·지지 오행과 생극) 60%. 최고 오행의 풀에서 `생년월일·시간·성별 + 날짜 + 오행` 해시로 하나. **매일 바뀌고**, 같은 입력이면 같은 날 같은 아이템(다시 생성해도 동일). 저장하지 않고 조회 시점에 계산하므로 `POST` 응답과 다음 날 `GET` 응답이 다를 수 있다
- `luckyPlace` 는 **행운의 장소**. 원국 오행 세력으로 일간 강약을 보고 균형을 보완하는 오행을 정해 그 오행의 장소 풀(3~4곳)에서 `팔자 + 날짜` 해시로 하나. 오행은 사람마다 고정, 장소는 **매일 바뀐다** (2026-09-15 결정). 동국대 캠퍼스 안
- 같은 생년월일·시간·성별로 다시 생성하면 **해석 문장·점수를 이전 결과에서 복사**한다 (LLM 호출 없음). 프롬프트나 점수 로직이 바뀐 뒤엔 다시 생성한다. `resultId`·`shareId`·닉네임·행운 아이템은 새로 만든다
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
    "destiny": { "title": "오래 사랑할 운명", "description": "..." },
    "fortunes": [ ... ],
    "elements": { "wood": 3, "fire": 2, "earth": 1, "metal": 1, "water": 1 },
    "elementMatch": { "element": "EARTH", "korean": "토", "reason": "..." },
    "luckyItem": "파란색 팔찌",
    "luckyPlace": "야외 무대",
    "compatibilities": [
      { "id": 15, "nickname": "민수", "score": 31, "tier": "SEUCHIM",  "createdAt": "2026-09-11T13:20:00Z" },
      { "id": 12, "nickname": "지현", "score": 92, "tier": "GUIIN", "createdAt": "2026-09-11T12:04:00Z" }
    ]
  }
}
```

- `compatibilities` 는 `createdAt` 내림차순
- `id` 는 궁합 ID(순번). `GET /api/compatibilities/{id}/reason` 에 쓴다 (§4)
- **상대의 생년월일·성별·resultId 는 내려보내지 않는다.** 닉네임과 점수만

### `GET /api/results/{resultId}/input`

결과를 만들 때 **입력한 값을 그대로** 돌려준다. 재입력 폼 자동 채움용. 필드 구성은 `POST /api/results` 요청과 같아서 그대로 폼에 넣으면 된다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "nickname": "도윤",
    "calendarType": "LUNAR",
    "birthDate": "2002-03-14",
    "isLeapMonth": false,
    "birthTime": "14:30",
    "gender": "FEMALE"
  }
}
```

- `birthDate` 는 **입력 원본**. 음력으로 입력했으면 음력 날짜가 나온다 (양력 변환값이 아니다)
- `birthTime` 은 `HH:mm`, 모르면 `null`
- 없는 `resultId` 면 `RESULT_NOT_FOUND` 404
- **`resultId` 는 본인만 아는 값이라는 전제다.** 이 응답에는 생년월일·성별이 들어 있으므로 프론트는 `resultId` 를 URL·화면에 노출하지 않는다. 공유에는 `shareId` 를 쓴다

---

### `PATCH /api/results/{resultId}`

닉네임만 바꾼다. 팔자·점수·해석·궁합 기록과 `resultId`·`shareId` 는 그대로다.

**Request**

```json
{ "nickname": "도윤" }
```

- `nickname` 필수, 8자 이하 (생성과 같은 규칙)

**Response 200** — `GET /api/results/{resultId}` 와 같은 구조

- 닉네임은 `compatibility` 에 복사돼 있지 않고 조회 시 `result` 에서 읽으므로 **공유 페이지와 친구의 궁합 목록에도 바로 반영된다**
- 형식 오류는 `INVALID_INPUT` 400, 없는 `resultId` 는 `RESULT_NOT_FOUND` 404
- 인증이 없다. `resultId` 는 본인만 아는 값이라는 전제이므로 프론트는 URL·화면에 노출하지 않는다

---

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
    "id": 12,
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
- `id` 는 궁합 ID(순번). 아래 상세 이유 조회에 쓴다

### `GET /api/compatibilities/{id}/reason`

궁합지도에서 친구 Row 를 눌러 상세 시트를 열 때 호출한다 (기능명세 4.7 · 5.10).
세 질문의 답을 한 번에 준다. **처음 열어볼 때 LLM 으로 생성해 저장**하므로 첫 호출만 느리고(최대 30초), 이후는 즉시 반환.
`{id}` = 궁합 생성 응답 또는 결과 조회 `compatibilities[].id`.

**Response 200**

```json
{
  "success": true,
  "data": {
    "why": "나무의 기운이 불의 기운을 살리는 사이라 ...",
    "together": "함께 있으면 나무의 기운을 가진 분이 먼저 ...",
    "conflict": "불의 기운 쪽이 먼저 달아오르기 쉬워요 ..."
  }
}
```

- `why` = "왜 나에게 귀인(찰떡·벗·스침)일까요?", `together` = "둘이 만나게 된다면?", `conflict` = "둘이 싸우게 된다면?". 각 3~4문장
- **두 사람이 같은 내용을 본다.** 글은 어느 한쪽을 "당신"으로 부르지 않고, 각자를 기운("나무의 기운을 가진 분")으로 가리킨다. 성별은 반영하지 않는다
- 없는 `id` 는 `COMPATIBILITY_NOT_FOUND` 404
- 생성 실패는 `LLM_UNAVAILABLE` 503. **해당 영역만 미노출**하고 궁합지도는 유지한다. 다시 호출하면 재시도된다
- 첫 호출이 느리므로 시트에 로딩 상태를 둔다. 같은 조합을 두 사람이 동시에 처음 열어도 저장은 한 번이고 둘이 같은 글을 받는다

---

## 5. 소개팅 사전등록

> **V1 에서 소개팅 프로필(`/api/dating/**`, 명세 확정 전)로 대체될 예정이다.** 대체될 때까지 이 API 는 그대로 동작하고, 대체 시점은 별도로 공지한다. 학교 메일 재학 인증은 소개팅에서도 유지된다.

### `POST /api/signups/photo-upload-url`

사진은 서버를 경유하지 않고 프론트가 S3에 직접 PUT 한다. 흐름: 이 엔드포인트 호출 → 응답의 `uploadUrl`에 파일 바이트를 `PUT`(Content-Type 헤더는 요청과 동일하게) → 응답의 `photoKey`를 `POST /api/signups`에 그대로 전달.

**Request**

```json
{ "contentType": "image/jpeg" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `contentType` | string | ✅ | `image/jpeg` \| `image/png` \| `image/webp` |

- 허용되지 않는 `contentType` → `INVALID_INPUT` 400

**Response 200**

```json
{
  "success": true,
  "data": {
    "uploadUrl": "https://wks-photos.s3.ap-northeast-2.amazonaws.com/signup-photos/3f2a9c1e-....jpg?X-Amz-...",
    "photoKey": "signup-photos/3f2a9c1e-....jpg",
    "expiresInSeconds": 600
  }
}
```

`uploadUrl`은 `expiresInSeconds` 동안만 유효한 presigned URL이다 (S3Presigner, `app.aws.s3.presigned-url-ttl-minutes` 설정값).

### `POST /api/signups`

**Request**

```json
{
  "email": "dev@dgu.ac.kr",
  "resultId": "3f2a9c1e-....",
  "gender": "MALE",
  "preferGender": "FEMALE",
  "name": "김동국",
  "contactMethod": "PHONE",
  "contactValue": "010-1234-5678",
  "department": "컴퓨터공학과",
  "mbti": "INFP",
  "bio": "축제를 좋아하는 컴공생입니다.",
  "photoKey": "signup-photos/3f2a9c1e-....jpg"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string \| `null` | ⚠️ 미정 | 최대 50자 |
| `contactMethod` | `PHONE` \| `INSTAGRAM` \| `null` | ⚠️ 미정 | |
| `contactValue` | string \| `null` | ⚠️ 미정 | `contactMethod`가 `PHONE`이면 휴대폰 번호 형식(`010-1234-5678` 등) 검증. `INSTAGRAM`이면 형식 제약 없음. 최대 100자 |
| `department` | string \| `null` | ⚠️ 미정 | 최대 100자 |
| `mbti` | string \| `null` | ⚠️ 미정 | 16유형(`^[EI][SN][TF][JP]$`) |
| `bio` | string \| `null` | ⚠️ 미정 | 최대 500자 |
| `photoKey` | string \| `null` | 선택 | `POST /api/signups/photo-upload-url` 응답값 그대로. 생략하면 사진 없이 신청 |

- `resultId` **nullable** — 사주 없이 신청하는 경로 허용
- 도메인 화이트리스트 위반 → `INVALID_EMAIL_DOMAIN` 400
- 중복 이메일 → `DUPLICATE_SIGNUP` 409
- `contactMethod: PHONE` + 휴대폰 번호 형식이 아닌 `contactValue` → `INVALID_INPUT` 400
- `photoKey`가 실제 S3 오브젝트와 매칭되지 않으면(미업로드·만료·위조) → `INVALID_INPUT` 400 (`PhotoUploadService.verifyPhotoExists`, headObject 조회)
- **(2026-09-15 변경)** 이름·연락처·학과·MBTI·자기소개를 수집한다 — 프론트 피그마 사전신청 화면에 맞춘 정책 변경. 단 **어느 필드를 필수로 할지는 기획 미확정**이라 서버는 6개 필드 전부 `null` 허용으로 구현했다. 빈 문자열(`""`)을 보내도 서버가 `null`로 처리한다. 필수 필드가 확정되면 서버 검증을 강화하고 이 표를 갱신한다
- **(2026-09-16 변경)** 사진 업로드를 이번 릴리즈에 추가한다 (사용자/곽도윤 결정, 기획 변경). presigned URL 방식, `photoKey`는 선택값

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
| 2026-09-22 (예정) | 카카오 로그인 (§9). 초안은 지금 볼 수 있다 |

목 데이터와 실제 응답이 어긋나면 **이 문서를 보고 잘못된 쪽을 고친다.**
"백엔드가 이미 그렇게 짰으니까"는 근거가 아니다.

---

## 9. 카카오 로그인 (V1 · 구현 전 초안)

> **초안이다.** 구현 PR 에서 확정하고 그때 이 표시를 지운다. 기획 원본은 `docs/plan.md` §1.1·§1.3·§8.1.
> 사주·궁합·공유 API 는 **로그인 없이 지금처럼 동작한다.** 로그인은 (1) 소개팅 이용, (2) 브라우저 저장소를 잃어도 내 결과·궁합지도를 다시 찾기 위한 것이다.

```
프론트: 카카오 인가 → redirectUri(프론트 콜백)로 code 수신
  → POST /api/auth/kakao {code, redirectUri, resultId?, ref?}
  → 서버가 Set-Cookie(wks_token, HttpOnly)로 토큰을 내려준다 — 프론트는 저장·첨부 안 해도 된다
  → 이후 인증이 필요한 API 호출은 credentials: 'include' 로 요청하면 브라우저가 쿠키를 자동으로 실어 보낸다
```

**2026-09-25, Bearer 헤더에서 쿠키로 전환했다** (의도적 결정, `docs/handoff.md` 참고). 프론트는 토큰을
직접 다루지 않는다 — `fetch`/`axios` 요청에 `credentials: 'include'`(axios는 `withCredentials: true`)만
켜면 된다. `localStorage`에 토큰을 저장하던 기존 로직은 제거한다.

### `POST /api/auth/kakao`

프론트가 받은 카카오 인가 코드를 넘기면 서버가 카카오와 교환해 로그인시키고 JWT 를 내려준다. 인증이 필요 없는 API 다.

**Request**

```json
{
  "code": "카카오가 준 인가 코드",
  "redirectUri": "https://threadoffate.site/auth/kakao/callback",
  "resultId": "3f2a9c1e-....",
  "ref": "PARTNER01"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `code` | string | ✅ | 카카오 인가 코드 |
| `redirectUri` | string | ✅ | 인가 요청에 쓴 것과 **같은 값**. 서버에 등록된 주소만 허용한다. 아니면 `INVALID_INPUT` 400 |
| `resultId` | string \| `null` | 선택 | 브라우저에 저장된 사주 결과 id. 있으면 아래 표대로 계정에 연결·복원한다. 없거나 형식이 틀리거나 존재하지 않아도 **로그인은 성공**한다 (연결만 생략) |
| `ref` | string \| `null` | 선택 | 제휴 코드. 잘못된 값은 조용히 무시하고 로그인은 성공한다 |

**Response 200**

```json
{
  "success": true,
  "data": {
    "isNewUser": true,
    "restoredResultId": null,
    "rewardGranted": null
  }
}
```

같은 응답에 `Set-Cookie: wks_token=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1296000`
헤더가 함께 온다. **`token` 필드는 응답 바디에 없다** (2026-09-25, 필드 삭제 — 이전엔 있었다). 쿠키는
HttpOnly라 프론트 JS가 값을 읽을 수 없고, 읽을 필요도 없다 — 브라우저가 알아서 이후 요청에 실어 보낸다.

- **만료 15일, 갱신 없음.** 만료되면 401 이 오고 다시 로그인한다
- 로그아웃은 `POST /api/auth/logout` 이 쿠키를 지운다 (아래). 서버 쪽 토큰 무효화는 없다 — 로그아웃 전에
  탈취된 토큰 사본은 만료까지 그대로 유효하다
- `isNewUser`: 이번 로그인으로 계정이 새로 만들어졌으면 `true`
- `rewardGranted`: 제휴·가입 보상이 지급됐으면 `{ "partnerName": "OO", "amount": 10 }`, 아니면 `null`. **소개팅·실 기능이 구현되기 전에는 항상 `null`**
- 카카오 동의항목은 받지 않는다. 서버는 회원번호만 쓴다. 이름·연락처는 소개팅 신청 폼에서 받는다

**결과 연결·복원 규칙** (`docs/plan.md` §1.1: 계정 결과가 항상 우선)

| 요청의 `resultId` | 계정에 결과 | 서버 동작 | `restoredResultId` |
|---|---|---|---|
| 있음 | 없음 | 브라우저 결과를 계정에 연결 | `null` (프론트는 브라우저 값을 그대로 쓴다) |
| 있음 | 있음 | **계정 결과 복원.** 브라우저 결과는 삭제·병합하지 않는다 | 계정 결과 id (프론트는 브라우저 저장값을 이걸로 교체) |
| 없음 | 있음 | 계정 결과 복원 | 계정 결과 id |
| 없음 | 없음 | 아무것도 하지 않는다 (사주 입력으로 유도) | `null` |

- 계정당 결과는 **1개**다. 이미 다른 계정에 연결된 `resultId` 는 연결하지 않는다 (로그인은 성공)
- 브라우저 결과를 지우거나 합치지 않으므로 친구 궁합 기록은 잃지 않는다. 병합은 V2 과제다

**에러**

| code | HTTP | 상황 |
|---|---|---|
| `INVALID_INPUT` | 400 | `code`·`redirectUri` 누락, 등록되지 않은 `redirectUri` |
| `INVALID_TOKEN` | 400 | 카카오 인가 코드 만료·이미 사용·위조 |
| `KAKAO_UNAVAILABLE` | 503 | 카카오 서버 오류·타임아웃. 잠시 후 재시도 안내 |

### `POST /api/auth/logout`

로그인 쿠키를 지운다(`Set-Cookie` 로 `Max-Age=0`). 인증 없이 호출할 수 있다 — 쿠키가 없거나 이미
만료된 상태에서 불러도 안전하게 아무 일도 안 한다.

**Response 200**

```json
{ "success": true, "data": null }
```

### `GET /api/me`

**인증 필요.** 로그인 상태와 내 계정 요약. 앱 진입 시 토큰이 유효한지 확인하는 용도로도 쓴다.

**Response 200**

```json
{
  "success": true,
  "data": {
    "memberId": 12,
    "hasResult": true,
    "hasDatingProfile": false,
    "threadBalance": 0
  }
}
```

- `hasResult`: 계정에 연결된 사주 결과가 있는지. 결과 자체는 `GET /api/me/result` 로 받는다
- `hasDatingProfile`·`threadBalance`: 소개팅·실 기능이 구현되기 전에는 `false` / `0` 고정이다. **필드는 처음부터 존재하고 값만 나중에 채워진다**
- 토큰이 없거나 만료·위조면 `UNAUTHENTICATED` 401

### `GET /api/me/result`

**인증 필요.** 계정에 연결된 **내 사주 결과**를 토큰만으로 돌려준다. 프론트가 `resultId` 를 저장해 두지 않았거나 잃어버렸어도, 로그인 상태면 내 결과를 다시 받을 수 있다.

**Response 200** — `GET /api/results/{resultId}` 와 **같은 구조** (`resultId`·`shareId`·`compatibilities` 포함)

- 응답의 `resultId` 로 `GET /api/results/{resultId}/input`, `PATCH /api/results/{resultId}` 등 기존 API 를 그대로 쓴다. 프론트는 이 값을 다시 저장하면 된다
- 계정에 연결된 결과가 없으면 `RESULT_NOT_FOUND` 404. 토큰이 없거나 만료·위조면 `UNAUTHENTICATED` 401
- 비로그인 사용자는 지금처럼 브라우저에 저장한 `resultId` 로 `GET /api/results/{resultId}` 를 쓴다. 이 API 는 **로그인 사용자의 복원용**이다
- 로그인 응답의 `restoredResultId` 와 같은 결과를 가리킨다 (계정당 결과는 1개)

### 인증 규칙

- 인증이 필요한 API: `GET /api/me`, `GET /api/me/result`, 소개팅(`/api/dating/**`), 이후 실(`/api/wallet/**`). **사주·궁합·공유·사전등록 API 는 쿠키 없이 동작하고, 보내도 무시된다**
- 인증이 필요한 API 는 반드시 `credentials: 'include'`(axios는 `withCredentials: true`) 로 호출한다 — 안 그러면 브라우저가 쿠키를 안 실어 보내 401 이 난다
- `401 UNAUTHENTICATED` 를 받으면 로그인 화면으로 보낸다 (프론트가 지울 토큰은 없다 — 쿠키는 서버가 관리)
- 토큰을 URL 쿼리에 넣지 않는다. `Authorization` 헤더도 쓰지 않는다 — **쿠키 하나로만 인증한다** (2026-09-25 전환)
- 프론트 콜백 주소(운영·로컬·netlify 등)를 **백엔드에 알려줘야 한다.** `redirectUri` 화이트리스트와 카카오 콘솔 등록에 필요하다

## 10. 소개팅 프로필·후보 추천

모든 `/api/dating/**` 요청에 로그인 쿠키(`wks_token`)가 필요하다 (§9 참고, `credentials: 'include'` 필수). 응답은 §1의 `ApiResponse` 형식이다. 여기서는 #84에서 추가한 API만 다룬다.

### 10.1 사진 업로드 준비 — `POST /api/dating/profile/photo`

이 API는 **사진 파일을 받지 않는다.** S3에 직접 업로드할 임시 주소와 사진 식별자를 발급한다.

**요청**

```json
{ "contentType": "image/jpeg" }
```

허용 형식은 `image/jpeg`, `image/png`, `image/webp`다. 그 외에는 `INVALID_INPUT` 400.

**응답 200 예시**

```json
{
  "success": true,
  "data": {
    "uploadUrl": "https://s3.example.com/temporary-signed-url",
    "photoId": "f1c4832a-0000-4000-8000-000000000002",
    "expiresInSeconds": 600
  }
}
```

프론트는 반환된 `uploadUrl`에 사진 파일을 `PUT`한다. `Content-Type`은 발급 요청의 `contentType`과 같아야 한다. PUT이 끝난 뒤 반환받은 `photoId`를 프로필 등록 요청에 넣는다. 예시 `photoId`는 실제 발급값으로 바꿔야 한다. `uploadUrl`은 만료되는 **업로드 전용 주소**이며 사진 조회 URL이 아니다.

### 10.2 프로필 등록 — `POST /api/dating/profile`

**요청 예시**

```json
{
  "email": "student@dgu.ac.kr",
  "name": "홍길동",
  "contactMethod": "PHONE",
  "contactValue": "010-3333-3333",
  "department": "컴퓨터공학과",
  "mbti": "ESTP",
  "bio": "안녕하세요",
  "photoId": "f1c4832a-0000-4000-8000-000000000002"
}
```

모든 필드가 필수다. `contactValue`는 문자열이며 `PHONE`이면 전화번호, `INSTAGRAM`이면 인스타그램 아이디(예: `my_insta_id`)를 넣는다. `photoId`는 **로그인한 회원에게 발급됐고 S3에 파일 업로드가 완료된 사진**이어야 한다. 계정에 연결된 사주 결과가 없으면 `RESULT_NOT_FOUND` 404다.

**응답 201 예시**

```json
{
  "success": true,
  "data": {
    "candidateId": "3f2a9c1e-0000-4000-8000-000000000001",
    "email": "student@dgu.ac.kr",
    "emailVerified": false,
    "name": "홍길동",
    "contactMethod": "PHONE",
    "contactValue": "010-3333-3333",
    "department": "컴퓨터공학과",
    "mbti": "ESTP",
    "bio": "안녕하세요",
    "photoId": "f1c4832a-0000-4000-8000-000000000002"
  }
}
```

`candidateId`는 소개팅 프로필 ID이며 다른 사람의 추천 카드에서도 이 값으로 표시된다. `photoId`는 사진 ID라 서로 다른 값이다. `emailVerified`는 학교 메일 인증 전에는 `false`다.

### 10.3 내 프로필 조회 — `GET /api/dating/profile/me`

응답 200의 `data`는 10.2의 프로필 등록 응답과 같은 구조다. 내 프로필이 없으면 `DATING_PROFILE_NOT_FOUND` 404다. V1에는 프로필 수정·사진 교체 API가 없다. `PATCH /api/dating/profile/me`는 제공하지 않으며 호출 시 `METHOD_NOT_ALLOWED` 405다.

### 10.4 현재 후보 — `GET /api/dating/recommendations`

최대 3개의 현재 후보를 반환한다. 후보는 학교 메일 인증을 마친 소개팅 신청 이성 중 기존 궁합 점수 내림차순으로 고른다. 한 번 카드에 나온 후보는 **리롤 이후 새 추천에서** 제외한다. 매칭이 성사되어도 기존 카드는 유지되고, 두 사람 모두 다른 사람의 추천 후보가 될 수 있다. 학교 이메일 인증 전에는 `DATING_NOT_VERIFIED` 403이다.

**응답 200 예시**

```json
{
  "success": true,
  "data": {
    "candidates": [{
      "rank": 1,
      "candidateId": "3f2a9c1e-0000-4000-8000-000000000001",
      "score": 90,
      "mbti": "INFP",
      "bio": "안녕하세요",
      "fields": {
        "photo": { "locked": true, "cost": 10 },
        "name": { "locked": true, "cost": 7 },
        "department": { "locked": true, "cost": 5 },
        "reason": { "locked": true, "cost": 3 }
      }
    }]
  }
}
```

후보가 없으면 `candidates: []`. 소개팅 카드의 궁합 등급 문구는 화면에서 고정으로 표시하므로 `tier`를 내려주지 않는다. 잠긴 개인정보·사진 URL·연락처는 응답에 없다. 현재 카드가 3장인 동안에는 새 신청자가 와도 단순 재조회로 교체되지 않는다. 빈자리가 있으면 다음 조회 때 새 후보로 채울 수 있다.

### #84 구현 상태와 남은 연동

- 위 API와 응답 형식은 구현됐지만 **실제 S3 버킷·권한·CORS를 이용한 URL 발급→PUT→프로필 등록 전체 흐름은 아직 검증 전**이다.
- 학교 메일 인증 완료를 소개팅 프로필에 반영하는 연동은 별도 작업이다. 연동 전에는 `GET /api/dating/recommendations`가 `DATING_NOT_VERIFIED` 403을 반환한다.
- 리롤·정보 해금·블러 미리보기용 썸네일 API는 #84에 포함되지 않는다. 매칭 요청은 §11을 본다.

---

## 11. 소개팅 매칭 요청·보관함

모두 로그인 쿠키(`wks_token`) 필수 (§9 참고). 매칭 요청은 **무료**이며 실을 차감하지 않는다. `candidateId`는 §10 추천 응답의 소개팅 프로필 ID다. `requestId`는 매칭 요청 자체의 ID로, 두 값은 다르다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| `POST` | `/api/dating/requests` | 현재 내 추천 카드에 있는 상대에게 요청. `201` |
| `GET` | `/api/dating/requests?box=sent` | 내가 보낸 요청 목록. `200` |
| `GET` | `/api/dating/requests?box=received` | 내가 받은 요청 목록. `200` |
| `POST` | `/api/dating/requests/{id}/accept` | 받은 사람만 수락. `200` |
| `POST` | `/api/dating/requests/{id}/reject` | 받은 사람만 거절. `200` |

요청 생성 본문:

```json
{"candidateId":"3fa85f64-5717-4562-b3fc-2c963f66afa6"}
```

요청 생성 응답 `201` 예시:

```json
{
  "success": true,
  "data": {
    "requestId": "312f3185-f114-4db0-a2fb-54d0669b7e33",
    "candidateId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "PENDING",
    "createdAt": "2026-09-24T12:00:00Z",
    "respondedAt": null,
    "contactMethod": null,
    "contactValue": null
  }
}
```

### 11.1 요청 목록 — `GET /api/dating/requests`

`box` 쿼리 파라미터는 필수이며 `sent`(내가 보낸 요청) 또는 `received`(내가 받은 요청)만 허용한다. 다른 값은 `INVALID_INPUT` 400이다. 최신 요청부터 반환하고, 목록이 비어 있으면 `{"success":true,"data":[]}`다. 내 소개팅 프로필이 없으면 `DATING_PROFILE_NOT_FOUND` 404다.

**보낸 요청 조회**: `GET /api/dating/requests?box=sent`

```json
{
  "success": true,
  "data": [{
    "requestId": "312f3185-f114-4db0-a2fb-54d0669b7e33",
    "candidateId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "PENDING",
    "createdAt": "2026-09-24T12:00:00Z",
    "respondedAt": null,
    "contactMethod": null,
    "contactValue": null
  }]
}
```

**받은 요청 조회**: `GET /api/dating/requests?box=received`

```json
{
  "success": true,
  "data": [{
    "requestId": "312f3185-f114-4db0-a2fb-54d0669b7e33",
    "candidateId": "84722660-622a-4d30-a5a5-0d6c736e8530",
    "status": "ACCEPTED",
    "createdAt": "2026-09-24T12:00:00Z",
    "respondedAt": "2026-09-24T12:05:00Z",
    "contactMethod": "PHONE",
    "contactValue": "010-3333-3333"
  }]
}
```

두 목록은 같은 요청 구조를 쓴다. `candidateId`는 **조회한 사람 기준 상대의 프로필 ID**여서, 같은 요청도 보낸 사람의 목록과 받은 사람의 목록에서 값이 다르다. `status`는 `PENDING`, `ACCEPTED`, `REJECTED` 중 하나다. `PENDING`과 `REJECTED`에서는 `contactMethod`·`contactValue`가 `null`이다. `ACCEPTED`에서는 **해당 요청의 상대 연락처만** 반환한다. 수락·거절 응답도 같은 객체이며 `respondedAt`이 채워진다. 요청 수에 상한은 없다.

본인 요청, 이미 요청한 두 사람의 재요청, 현재 추천 카드에 없는 상대는 거절한다. 한 쌍은 방향을 바꿔도 한 번만 요청할 수 있으며, 거절 후에도 같은 쌍으로 다시 요청할 수 없다. 요청을 보내려면 내 학교 메일 인증이 완료돼 있어야 한다. 수락 후에도 양쪽은 계속 소개팅을 이용하고 다른 사람의 추천 후보에 남는다. 수락이 다른 요청의 상태를 바꾸지는 않는다. 실 기능과는 별개다.

학교 메일 인증 완료를 프로필에 반영하는 연동이 끝나기 전에는 추천 조회가 403이어서, 실제 추천 카드에서 매칭 요청까지 이어지는 흐름은 사용할 수 없다 (§10 구현 상태).

# api.md — 프론트 공유용 API 명세서

축제 사주 서비스 백엔드 API 전체 명세입니다. 프론트 팀 전달용으로 루트에 둡니다.

> 내부 원본 문서는 `docs/api-spec.md` 입니다. 스펙이 바뀌면 그 문서가 먼저 바뀌고 이 파일도 함께 갱신됩니다.
> 실시간으로 직접 호출해보고 싶다면 Swagger UI를 쓰세요 — 요청/응답 스키마가 항상 최신 코드 기준으로 자동 생성됩니다.

## Base URL

| 환경 | URL |
|---|---|
| 운영(EC2) | `https://api.threadoffate.site` |
| 로컬 개발 | `http://localhost:8080` |

| 항목 | 경로 |
|---|---|
| API prefix | `/api` |
| Swagger UI | `/swagger-ui.html` |
| OpenAPI JSON | `/v3/api-docs` |

예: 운영 Swagger → `https://api.threadoffate.site/swagger-ui.html`

---

## 1. 공통

### 응답 포맷

```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{
  "success": false,
  "error": { "code": "RESULT_NOT_FOUND", "message": "사주 결과를 찾을 수 없습니다.", "traceId": "a1b2c3" }
}
```

- HTTP 상태코드도 함께 맞춰서 내려감
- 인증이 필요한 API 는 로그인 쿠키(`wks_token`, HttpOnly)로 인증합니다 (§6). `credentials: 'include'` 로 호출해야 합니다. 사주·궁합·공유 API 는 인증이 없습니다
- `error.message`는 사용자에게 그대로 보여줘도 되는 한국어 문구
- `traceId`는 장애 문의 시 로그 추적용 (화면에 노출해도 무방)
- JSON 키는 camelCase, 날짜는 `yyyy-MM-dd`, 시각은 ISO-8601 UTC(`Z`)
- null 필드도 키 자체는 유지됨 (`undefined` 체크 안 해도 됨)

### 에러 코드

| code | HTTP | 상황 |
|---|---|---|
| `INVALID_INPUT` | 400 | 유효성 검증 실패 (필수 쿼리 파라미터 누락 포함) |
| `RESULT_NOT_FOUND` | 404 | resultId / shareId 없음 |
| `SELF_COMPATIBILITY` | 400 | 자기 자신과 궁합 요청 |
| `DUPLICATE_SIGNUP` | 409 | 이미 신청한 이메일 |
| `INVALID_EMAIL_DOMAIN` | 400 | 학교 웹메일 아님 |
| `INVALID_TOKEN` | 400 | 인증 토큰 만료·위조·재사용 |
| `LLM_UNAVAILABLE` | 503 | 해석 생성 실패 |
| `INTERNAL_ERROR` | 500 | 그 외 서버 오류 |
| `NOT_FOUND` | 404 | 존재하지 않는 경로 |

---

## 2. 사주 결과

### `POST /api/results` — 사주 생성

계산 + 해석 생성. **3~10초 걸릴 수 있음.** 로딩 UX 필수.

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

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `nickname` | string | ✅ | 1~8자, 공백만 불가 |
| `calendarType` | `SOLAR` \| `LUNAR` | ✅ | |
| `birthDate` | `yyyy-MM-dd` | ✅ | 1950-01-01 ~ 오늘. `LUNAR`면 음력 날짜 |
| `isLeapMonth` | boolean | - | `LUNAR`이고 윤달이면 `true`. 생략 시 `false`. `SOLAR`면 무시 |
| `birthTime` | `HH:mm` \| `null` | - | 모르면 `null`. 시진(2시간 단위) 선택 UI면 칸의 가운데 시각을 보낸다 (예: 묘시 05:30~07:30 → `"06:30"`). **자시는 두 칸**: `00:00~01:30` → `"00:45"`, `23:30~24:00` → `"23:45"` |
| `gender` | `MALE` \| `FEMALE` | ✅ | |

- 음력 입력 시 서버가 한국 음력 기준으로 양력 변환 후 계산·저장 (응답은 항상 양력). 존재하지 않는 음력 날짜는 400 `INVALID_INPUT`
- 출생 지역은 받지 않음 (시진 단위 입력이라 지역 시차 보정 불필요, 서버는 서울 경도 기준 계산)

**Response 201**
```json
{
  "success": true,
  "data": {
    "resultId": "3f2a9c1e-1234-4a1b-8c1a-abcdef123456",
    "shareId": "7b91d26f-5678-4c2d-9e3f-fedcba654321",
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
    "luckyItem": "파란 부채",
    "luckyPlace": "팔정도 앞",
    "compatibilities": []
  }
}
```

- 화면 고정 문구("당신의 운명은")는 프론트에서 표시
- `fortunes` 순서는 `MARRIAGE` → `CHILDREN` → `LOVE` 고정
- `zodiac`은 십이간지(`RAT` `OX` `TIGER` `RABBIT` `DRAGON` `SNAKE` `HORSE` `GOAT` `MONKEY` `ROOSTER` `DOG` `PIG`). **입춘 기준**이라 양력 연도 계산과 1~2월생에서 다를 수 있음 — 프론트가 직접 계산하지 말 것. 캐릭터 이름·이모지 매핑은 프론트 몫
- `grade` 6단계: `SS` 94~100 · `S` 84~93 · `A+` 74~83 · `A` 64~73 · `B+` 52~63 · `B` 0~51
- `elements`는 사주 원국 각 글자의 오행 개수입니다. 출생 시간이 있으면 합계 8, 모르면 시주를 제외해 합계 6입니다. **해설 문장이 말하는 "많은 기운"도 이 개수 기준**이라 화면과 어긋나지 않습니다
- `destiny.title`은 연애·결혼·자녀 상/하 조합 8종 중 하나입니다 (기획 피드백 1차 문구). 점수로 계산하므로 저장하지 않습니다
- `luckyItem`/`luckyPlace`는 **오늘 기준으로 매일 바뀜**. 저장하지 않고 조회 시점에 계산하므로 다음 날 `GET` 응답이 달라질 수 있음. 장소는 동국대 캠퍼스 내
- 같은 생년월일·시간·성별로 다시 생성하면 **해석 문장·점수를 이전 결과에서 복사**합니다 (LLM 호출 없음). `resultId`·`shareId`·닉네임·행운 아이템은 새로 만들어집니다
- 본인 결과 조회에는 `resultId`, 친구 공유 URL에는 `shareId` 사용

### `GET /api/results/{resultId}` — 결과 조회

본인 결과 재방문. **LLM 재호출 없이 DB에서 반환.** 가장 트래픽 몰리는 엔드포인트.

**Response 200**: `POST /api/results`와 동일한 구조 + `compatibilities`에 실제 데이터

```json
{
  "success": true,
  "data": {
    "resultId": "3f2a9c1e-....",
    "shareId": "7b91d26f-....",
    "nickname": "도윤",
    "zodiac": "HORSE",
    "destiny": { "title": "...", "description": "..." },
    "fortunes": [ ... ],
    "elements": { "wood": 3, "fire": 2, "earth": 1, "metal": 1, "water": 1 },
    "luckyItem": "파란 부채",
    "luckyPlace": "팔정도 앞",
    "compatibilities": [
      { "nickname": "민수", "score": 31, "tier": "SEUCHIM", "createdAt": "2026-09-11T13:20:00Z" },
      { "nickname": "지현", "score": 92, "tier": "GUIIN",   "createdAt": "2026-09-11T12:04:00Z" }
    ]
  }
}
```

- `compatibilities`는 `createdAt` 내림차순
- **상대의 생년월일·성별·resultId는 내려가지 않음.** 닉네임과 점수만
- 잘못된 UUID 형식 → 400 `INVALID_INPUT`, 없는 resultId → 404 `RESULT_NOT_FOUND`

### `GET /api/results/{resultId}/input` — 입력값 조회

결과를 만들 때 **입력한 값을 그대로** 돌려줍니다. 재입력 폼 자동 채움용이고, 필드 구성은 `POST /api/results` 요청과 같아서 그대로 폼에 넣으면 됩니다.

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

- `birthDate` 는 **입력 원본**입니다. 음력으로 입력했으면 음력 날짜가 나옵니다 (양력 변환값이 아님)
- `birthTime` 은 `HH:mm`, 모르면 `null`
- 없는 `resultId` 면 404 `RESULT_NOT_FOUND`
- **이 응답에는 생년월일·성별이 들어 있으므로 `resultId` 를 URL·화면에 노출하지 마세요.** 공유에는 `shareId` 를 씁니다

### `PATCH /api/results/{resultId}` — 닉네임 수정

닉네임만 바꿉니다. 팔자·점수·해석·궁합 기록과 `resultId`·`shareId` 는 그대로입니다.

**Request**
```json
{ "nickname": "도윤" }
```

**Response 200**: `GET /api/results/{resultId}` 와 같은 구조

- `nickname` 필수, 8자 이하 (생성과 같은 규칙)
- 닉네임은 조회 시점에 읽으므로 **공유 페이지와 친구의 궁합 목록에도 바로 반영**됩니다
- 형식 오류는 400 `INVALID_INPUT`, 없는 `resultId` 는 404 `RESULT_NOT_FOUND`
- 인증이 없습니다. `resultId` 는 본인만 아는 값이라는 전제이므로 URL·화면에 노출하지 마세요

### `GET /api/shares/{shareId}` — 공유 결과 조회

친구가 공유 링크로 진입할 때 링크 주인의 공개 결과와 궁합 지도를 조회.

**Response 200**: 결과 조회와 같은 필드지만 `resultId`/`shareId` 없음

```json
{
  "success": true,
  "data": {
    "nickname": "서연",
    "zodiac": "HORSE",
    "destiny": { "title": "...", "description": "..." },
    "fortunes": [ ... ],
    "elements": { "wood": 3, "fire": 2, "earth": 1, "metal": 1, "water": 1 },
    "luckyItem": "파란 부채",
    "luckyPlace": "팔정도 앞",
    "compatibilities": [ ... ]
  }
}
```

---

## 3. 친구 궁합

### `POST /api/shares/{shareId}/compatibility` — 궁합 생성

친구가 공유 링크에서 `POST /api/results`로 자기 정보를 입력한 직후 호출.
`{shareId}` = 링크 주인의 공개 ID, `guestResultId` = 친구가 방금 생성한 결과 ID.

**Request**
```json
{ "guestResultId": "3f2a9c1e-...." }
```

**Response**: 신규 생성 **201** / 기존 조합이면 재계산 없이 **200**
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
- `score(A,B) == score(B,A)` 항상 보장
- `tier` 구간: `GUIIN` 90~100 · `CHALTTEOK` 75~89 · `BEOT` 61~74 · `SEUCHIM` 0~60

---

## 4. 소개팅 사전등록

> **V1 에서 소개팅 프로필(명세 확정 전)로 대체될 예정입니다.** 대체될 때까지 이 API 는 그대로 동작하며 대체 시점은 별도로 공지합니다. 학교 메일 재학 인증은 소개팅에서도 유지됩니다.

### `POST /api/signups/photo-upload-url` — 사진 업로드 URL 발급

사진은 서버를 거치지 않고 프론트가 S3에 직접 올린다. 순서: 이 엔드포인트로 업로드용 URL 발급 → 받은 `uploadUrl`에 파일 바이트를 그대로 `PUT` → 응답의 `photoKey`를 아래 `POST /api/signups` 요청에 담아 보낸다.

**Request**
```json
{ "contentType": "image/jpeg" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `contentType` | string | ✅ | `image/jpeg` \| `image/png` \| `image/webp` 중 하나 |

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

- `uploadUrl`로 `PUT` 요청 시 **`Content-Type` 헤더를 요청에 보낸 값과 동일하게** 설정해야 한다 (다르면 S3가 서명 불일치로 거부함)
- `uploadUrl`은 발급 후 `expiresInSeconds`(현재 600초) 동안만 유효
- 허용되지 않는 `contentType` → `INVALID_INPUT` 400

### `POST /api/signups` — 사전등록 신청

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
| `email` | string | ✅ | 학교 웹메일. 이메일 형식 검증 |
| `resultId` | string \| `null` | - | 사주 없이 신청하는 경로 허용 |
| `gender` | `MALE` \| `FEMALE` | ✅ | |
| `preferGender` | `MALE` \| `FEMALE` | ✅ | |
| `name` | string \| `null` | ⚠️ 미정 | 최대 50자 |
| `contactMethod` | `PHONE` \| `INSTAGRAM` \| `null` | ⚠️ 미정 | 연락 수단 선택 |
| `contactValue` | string \| `null` | ⚠️ 미정 | `PHONE`이면 휴대폰 번호 형식 검증(`010-1234-5678` 등), `INSTAGRAM`이면 형식 제약 없음. 최대 100자 |
| `department` | string \| `null` | ⚠️ 미정 | 최대 100자 |
| `mbti` | string \| `null` | ⚠️ 미정 | 16유형만 허용 (예: `INFP`) |
| `bio` | string \| `null` | ⚠️ 미정 | 자기소개, 최대 500자 |
| `photoKey` | string \| `null` | 선택 | `POST /api/signups/photo-upload-url` 응답에서 받은 값 그대로. 안 보내면 사진 없이 신청 |

- 도메인 화이트리스트 위반 → `INVALID_EMAIL_DOMAIN` 400 (⚠️ 현재 화이트리스트가 팀 결정 전이라 **검증 자체를 건너뛰는 중** — 학교 도메인 확정되면 서버 설정만 바뀌고 API 형태는 그대로임)
- `resultId`를 보냈는데 존재하지 않으면 `RESULT_NOT_FOUND` 404
- 중복 이메일 → `DUPLICATE_SIGNUP` 409
- `contactMethod: "PHONE"`인데 휴대폰 번호 형식이 아니면 `INVALID_INPUT` 400 (값을 아예 안 보내면 검증 생략)
- `mbti`를 보냈는데 16유형 형식이 아니면 `INVALID_INPUT` 400
- `photoKey`를 보냈는데 실제로 S3에 업로드된 적 없으면(만료됐거나 위조된 key) `INVALID_INPUT` 400
- ⚠️ **(2026-09-15 변경)** 이전에는 "이름·전화번호는 받지 않음"이었으나, 피그마 사전신청 화면에 맞춰 정책이 변경됐다. **다만 이 6개 필드가 필수인지 선택인지는 아직 기획 미확정** — 지금은 서버가 전부 `null`(빈 문자열 `""`도 `null`로 처리)을 허용한다. 프론트는 일단 값이 있으면 보내고, 없으면 필드째로 생략하거나 `null`로 보내면 된다. 필수 여부 확정되면 이 문서와 서버 검증을 같이 갱신함
- **(2026-09-16 변경)** 사진 업로드가 이번 릴리즈에 추가됐다(기획 결정). `photoKey`는 선택값 — 안 보내도 신청 가능

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

`mailSent: false`여도 신청은 성공(201). SMTP 실패가 신청을 롤백시키지 않음 — 프론트는 이 경우 재발송 안내 노출.

### `POST /api/signups/resend` — 인증 메일 재발송

**Request**
```json
{ "email": "dev@dgu.ac.kr" }
```

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

- 신청 내역 없는 이메일 / 이미 인증 완료된 이메일 → 둘 다 `INVALID_INPUT` 400 (전용 에러코드 없음)
- `mailSent: false`여도 200 (§2 `POST /api/results`와 동일 원칙 — SMTP 실패가 요청 자체를 실패시키지 않음)

### `GET /api/signups/verify?token={token}` — 이메일 인증

메일 속 매직링크 클릭 시 호출되는 엔드포인트. **성공 시 302로 프론트 완료 페이지로 리다이렉트.**

- 리다이렉트 대상 URL은 서버 설정값 (프론트 팀이 알려준 완료 페이지 URL로 세팅되어 있어야 함)
- 토큰 유효기간 30분, 사용 후 재사용 불가
- 만료·위조·재사용 토큰, 또는 `token` 파라미터 누락 → JSON 바디로 400 `INVALID_TOKEN` / `INVALID_INPUT` (리다이렉트 안 됨)

---

## 5. 헬스체크

### `GET /api/health`

```json
{ "success": true, "data": { "status": "UP" } }
```

---

## 6. 카카오 로그인 (V1 · 구현 전 초안)

> **초안입니다.** 구현이 끝나면 확정하고 이 표시를 지웁니다. 사주·궁합·공유 API 는 **로그인 없이 지금처럼 동작합니다.**
> 로그인은 소개팅 이용, 그리고 브라우저 저장소를 잃어도 내 결과·궁합지도를 다시 찾기 위한 것입니다.

```
프론트: 카카오 인가 → redirectUri(프론트 콜백)로 code 수신
  → POST /api/auth/kakao {code, redirectUri, resultId?, ref?}
  → 서버가 Set-Cookie(wks_token, HttpOnly)로 토큰을 내려줍니다 — 프론트는 저장·첨부 안 해도 됩니다
  → 이후 인증이 필요한 API 는 credentials: 'include' 로 호출하면 브라우저가 쿠키를 자동으로 실어 보냅니다
```

**2026-09-25, Bearer 헤더에서 쿠키로 전환했습니다.** `fetch`/`axios` 요청에
`credentials: 'include'`(axios는 `withCredentials: true`)만 켜면 됩니다. `localStorage` 에 토큰을
저장하던 기존 로직은 제거해 주세요.

### `POST /api/auth/kakao` — 카카오 로그인

인증이 필요 없는 API 입니다. 프론트가 받은 카카오 인가 코드를 넘기면 서버가 로그인 처리하고 JWT 를 내려줍니다.

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
| `redirectUri` | string | ✅ | 인가 요청에 쓴 것과 **같은 값**. 서버에 등록된 주소만 허용 (아니면 `INVALID_INPUT` 400) |
| `resultId` | string \| `null` | 선택 | 브라우저에 저장된 사주 결과 id. 없거나 형식이 틀리거나 존재하지 않아도 **로그인은 성공**하고 연결만 생략됩니다 |
| `ref` | string \| `null` | 선택 | 제휴 코드. 잘못된 값은 조용히 무시되고 로그인은 성공합니다 |

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
헤더가 함께 옵니다. **`token` 필드는 더 이상 응답 바디에 없습니다** (2026-09-25, 필드 삭제). 쿠키는
HttpOnly라 프론트 JS가 값을 읽을 수 없고, 읽을 필요도 없습니다.

- **만료 15일, 갱신 없음.** 만료되면 401 이 오고 다시 로그인하면 됩니다
- 로그아웃은 `POST /api/auth/logout` 을 호출하면 서버가 쿠키를 지웁니다(아래)
- `isNewUser`: 이번 로그인으로 계정이 새로 만들어졌으면 `true`
- `rewardGranted`: 보상이 지급됐으면 `{ "partnerName": "OO", "amount": 10 }`, 아니면 `null`. **소개팅·실 기능이 나오기 전에는 항상 `null`**
- 카카오 동의항목은 받지 않습니다 (회원번호만 사용)

**결과 연결·복원 규칙** — 계정에 저장된 결과가 항상 우선합니다.

| 요청의 `resultId` | 계정에 결과 | 서버 동작 | `restoredResultId` |
|---|---|---|---|
| 있음 | 없음 | 브라우저 결과를 계정에 연결 | `null` (브라우저 값을 그대로 사용) |
| 있음 | 있음 | **계정 결과 복원.** 브라우저 결과는 삭제·병합하지 않음 | 계정 결과 id (브라우저 저장값을 이걸로 교체) |
| 없음 | 있음 | 계정 결과 복원 | 계정 결과 id |
| 없음 | 없음 | 아무것도 하지 않음 (사주 입력으로 유도) | `null` |

- 계정당 결과는 1개입니다. 이미 다른 계정에 연결된 `resultId` 는 연결되지 않습니다 (로그인은 성공)

**에러**

| code | HTTP | 상황 |
|---|---|---|
| `INVALID_INPUT` | 400 | `code`·`redirectUri` 누락, 등록되지 않은 `redirectUri` |
| `INVALID_TOKEN` | 400 | 카카오 인가 코드 만료·이미 사용·위조 |
| `KAKAO_UNAVAILABLE` | 503 | 카카오 서버 오류·타임아웃. 잠시 후 재시도 안내 |

### `POST /api/auth/logout` — 로그아웃

로그인 쿠키를 지웁니다. 인증 없이 호출할 수 있습니다.

**Response 200**
```json
{ "success": true, "data": null }
```

### `GET /api/me` — 내 정보

**인증 필요.** 로그인 상태 요약이고, 앱 진입 시 토큰이 아직 유효한지 확인하는 용도로도 쓸 수 있습니다.

**Response 200**
```json
{
  "success": true,
  "data": { "memberId": 12, "hasResult": true, "hasDatingProfile": false, "threadBalance": 0 }
}
```

- `hasDatingProfile`·`threadBalance` 는 소개팅·실 기능이 나오기 전에는 `false` / `0` 고정입니다. 필드는 처음부터 존재하고 값만 나중에 채워집니다
- `hasResult` 가 `true` 이면 `GET /api/me/result` 로 내 결과를 받을 수 있습니다
- 토큰이 없거나 만료·위조면 `UNAUTHENTICATED` 401

### `GET /api/me/result` — 내 결과

**인증 필요.** 계정에 연결된 **내 사주 결과**를 토큰만으로 돌려줍니다. `resultId` 를 저장해 두지 않았거나 잃어버렸어도 로그인 상태면 내 결과를 다시 받을 수 있습니다.

**Response 200**: `GET /api/results/{resultId}` 와 **같은 구조** (`resultId`·`shareId`·`compatibilities` 포함)

- 응답의 `resultId` 로 `GET /api/results/{resultId}/input`, `PATCH /api/results/{resultId}` 등 기존 API 를 그대로 쓰면 됩니다. 이 값을 다시 저장해 두세요
- 계정에 연결된 결과가 없으면 `RESULT_NOT_FOUND` 404, 토큰이 없거나 만료·위조면 `UNAUTHENTICATED` 401
- 비로그인 사용자는 지금처럼 브라우저에 저장한 `resultId` 로 `GET /api/results/{resultId}` 를 씁니다. 이 API 는 **로그인 사용자의 복원용**입니다

### 인증 규칙

- 인증이 필요한 API: `GET /api/me`, `GET /api/me/result`, 소개팅(`/api/dating/**`), 이후 실(`/api/wallet/**`). **사주·궁합·공유·사전등록 API 는 쿠키 없이 동작**합니다
- 인증이 필요한 API 는 반드시 `credentials: 'include'`(axios는 `withCredentials: true`) 로 호출하세요 — 안 그러면 브라우저가 쿠키를 안 실어 보내 401이 납니다
- `401 UNAUTHENTICATED` 를 받으면 로그인 화면으로 보내세요 (지울 토큰은 없습니다 — 쿠키는 서버가 관리)
- 토큰을 URL 쿼리에 넣지 마세요. `Authorization` 헤더도 쓰지 않습니다 — **쿠키 하나로만 인증합니다** (2026-09-25 전환)
- **프론트 콜백 주소(운영·로컬·netlify 등)를 백엔드에 알려주세요.** `redirectUri` 화이트리스트와 카카오 콘솔 등록에 필요합니다

---

## 참고

- 리소스명은 복수형 명사(`/results`, `/signups`), 동사 없음
- 위 스펙과 실제 응답이 어긋나면 `docs/api-spec.md` 기준으로 백엔드가 고침 — "코드가 이미 그렇게 짜여있어서"는 근거가 안 됨
- API 변경 이력은 `docs/handoff.md`의 "프론트에 공지한 API 변경" 표 참고

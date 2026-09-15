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
      "title": "사랑이 앞서 걷는 길",
      "description": "당신은 특별한 운명을 타고났습니다. 앞으로 좋은 흐름을 맞이하게 됩니다."
    },
    "fortunes": [
      { "category": "MARRIAGE", "grade": "SS", "content": "결혼운에 대한 설명" },
      { "category": "CHILDREN", "grade": "A+", "content": "자녀운에 대한 설명" },
      { "category": "LOVE",     "grade": "B",  "content": "연애운에 대한 설명" }
    ],
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
- `destiny.title`은 결혼·자녀·연애 상/하 조합 8종 중 하나 (문구는 기획 확정 전 임시값)
- `luckyItem`/`luckyPlace`는 **오늘 기준으로 매일 바뀜**. 저장하지 않고 조회 시점에 계산하므로 다음 날 `GET` 응답이 달라질 수 있음. 장소는 동국대 캠퍼스 내
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
  "bio": "축제를 좋아하는 컴공생입니다."
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

- 도메인 화이트리스트 위반 → `INVALID_EMAIL_DOMAIN` 400 (⚠️ 현재 화이트리스트가 팀 결정 전이라 **검증 자체를 건너뛰는 중** — 학교 도메인 확정되면 서버 설정만 바뀌고 API 형태는 그대로임)
- `resultId`를 보냈는데 존재하지 않으면 `RESULT_NOT_FOUND` 404
- 중복 이메일 → `DUPLICATE_SIGNUP` 409
- `contactMethod: "PHONE"`인데 휴대폰 번호 형식이 아니면 `INVALID_INPUT` 400 (값을 아예 안 보내면 검증 생략)
- `mbti`를 보냈는데 16유형 형식이 아니면 `INVALID_INPUT` 400
- ⚠️ **(2026-09-15 변경)** 이전에는 "이름·전화번호는 받지 않음"이었으나, 피그마 사전신청 화면에 맞춰 정책이 변경됐다. **다만 이 6개 필드가 필수인지 선택인지는 아직 기획 미확정** — 지금은 서버가 전부 `null`(빈 문자열 `""`도 `null`로 처리)을 허용한다. 프론트는 일단 값이 있으면 보내고, 없으면 필드째로 생략하거나 `null`로 보내면 된다. 필수 여부 확정되면 이 문서와 서버 검증을 같이 갱신함
- **사진 업로드는 이번 릴리즈에 없음** — 프론트에서 사진 필드는 2차 릴리즈 전까지 UI만 두거나 비활성화 처리 필요

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

## 참고

- 리소스명은 복수형 명사(`/results`, `/signups`), 동사 없음
- 위 스펙과 실제 응답이 어긋나면 `docs/api-spec.md` 기준으로 백엔드가 고침 — "코드가 이미 그렇게 짜여있어서"는 근거가 안 됨
- API 변경 이력은 `docs/handoff.md`의 "프론트에 공지한 API 변경" 표 참고

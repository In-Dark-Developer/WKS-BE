# 소개팅 관리자 API · admin.html (#147)

운영자가 소개팅 프로필을 조회·수정·비활성화·사진 교체·삭제한다. **프론트 계약(`api-spec.md`)이 아니다.**
탈퇴·삭제 요청은 운영자가 직접 처리한다는 결정(`plan.md` §12)을 SQL 대신 이 API 로 한다.
관련 코드: `admin/`(인터셉터·컨트롤러), `dating/DatingAdminService`, `static/admin.html`.

## 인증

- 헤더 `X-Admin-Token: <ADMIN_TOKEN>`. 환경변수 `ADMIN_TOKEN` 하나로 지킨다(`admin/AdminAuthInterceptor`).
- **비어 있으면 `/api/admin/**` 전부 401**, 나머지 서비스는 정상. 운영·개발 `.env` 에 각각 다른 값.
- 생성: `openssl rand -hex 32`. 넣는 곳: EC2 `/opt/wks/.env`(운영)·`/opt/wks-dev/.env`(개발). 반영은 다음 배포(`docker compose up -d`).
- Spring Security·역할 체계 없음. 사람 구분 없는 토큰 1개라 **누가 처리했는지 추적 불가.** 관리자 2명 이상이 상시 쓰면 `member.role` 로 전환.
- 유출 시: `.env` 값 교체 → 앱 재기동. 축제 후 값을 비우면 API·페이지 동시 무력화.
- 토큰을 저장소·Slack 채널·`.env*.example` 에 쓰지 않는다. 기획자에게는 DM.

## 운영자 페이지 `/admin.html`

`https://api.threadoffate.site/admin.html` (개발: `api-dev`). 빌드 없는 정적 파일 한 장. 같은 도메인이라 CORS 없음.

- 토큰은 `sessionStorage` 에만 둔다 — 탭 닫으면 사라진다. 401 이 오면 토큰 화면으로 돌아간다.
- 이메일로 찾기 → 카드(사진·전 필드·활성 상태) → 수정 저장 / 비활성화·활성화 / 사진 교체 / 삭제.
- 삭제 버튼은 이메일을 다시 입력해 일치해야 켜진다. 되돌릴 수 없다.
- 요청이 하나 나가 있는 동안에는 동작 버튼(찾기·저장·활성화 토글·사진·삭제·토큰 지우기·통계 새로고침)이 전부 잠긴다. 연타로 사진이 두 번 올라가거나 요청끼리 겹치지 않게.
- 탭 두 개: "소개팅 프로필"(위 기능) / "축제 통계"(아래 `GET /api/admin/stats`). 통계 탭은 처음 열 때 한 번 불러오고 이후엔 새로고침 버튼으로만.
- 사진 교체는 브라우저가 S3 presigned URL 에 직접 PUT 한다. **버킷 CORS 에 api 오리진이 있어야 한다**(2026-09-29 `api.`·`api-dev.` 등록 완료). 로컬 `localhost:8080` 은 없어서 로컬에서는 사진만 안 된다.

## 엔드포인트

기본 경로 `/api/admin/dating/profiles`. 응답은 공통 포맷(`{success, data}` / `{success:false, error:{code,message}}`).

| 메서드 | 경로 | 동작 | 에러 |
|---|---|---|---|
| GET | `?email=` | 이메일로 단건 조회 (대소문자 무시) | 404 `DATING_PROFILE_NOT_FOUND` |
| GET | `/{profileId}` | 조회 | 404 |
| PATCH | `/{profileId}` | 부분 수정. 바디의 null 필드는 그대로. `email`·`name`·`contactMethod`·`contactValue`·`department`·`mbti`·`bio` | 400 `INVALID_INPUT`(전화 형식 등)·`INVALID_EMAIL_DOMAIN`, 409 `DATING_PROFILE_CONFLICT`(이메일 중복) |
| POST | `/{profileId}/deactivate` | 내리기. 멱등 | 404 |
| POST | `/{profileId}/activate` | 되살리기. 멱등 | 404 |
| POST | `/{profileId}/photo-upload-url` | `{contentType}`(`image/jpeg`·`image/png`) → `{uploadUrl, photoId, expiresInSeconds}`. 사진은 그 회원 소유로 발급 | 400 |
| PATCH | `/{profileId}/photo` | `{photoId}` 를 프로필에 붙이고 옛 사진 행·S3 원본·썸네일 삭제 | 400 `INVALID_INPUT`(남의 사진·업로드 안 됨) |
| DELETE | `/{profileId}` | 프로필 삭제 | 404 |

응답 `data`: `profileId, memberId, email, name, contactMethod, contactValue, department, mbti, bio, photoId, photoUrl(서명 URL, 10분), verifiedAt, deactivatedAt, createdAt`. 운영자용이라 잠긴 필드 없이 전부 내린다.

## 축제 통계 `GET /api/admin/stats`

`admin.html` 의 "축제 통계" 탭이 부른다. 같은 `X-Admin-Token`. 파라미터 없음 — 기간은 코드에 고정(`AdminStatsService.FESTIVAL_FIRST_DAY`, 3일).
기획 결정은 `plan.md` §1.5. 관련 코드: `admin/AdminStatsController`·`AdminStatsService`·`dto/AdminStatsResponse`.

- **범위**: `created_at` 이 2026-09-29 00:00 ~ 10-02 00:00 **KST** 인 행. 날짜·시(0~23) 버킷도 KST.
- **시간대**: 운영 EC2·컨테이너·DB 세션 시간대가 KST 가 아니다. 그래서 기본값에 기대지 않는다 — 경계는 Java 에서 KST 절대 시각으로 만들어 넘기고, 버킷은 SQL `AT TIME ZONE 'Asia/Seoul'` 로 자른다. 테스트(`AdminStatsFlowTest`)는 JVM 시간대를 `America/Los_Angeles` 로 바꿔 놓고 경계(23:59:59 / 00:00)를 확인한다.
- **상태 값은 조회 시점**: 인증·비활성·요청 status 는 "기간 안에 만들어진 행의 지금 상태"다. 운영자가 삭제한 프로필은 수에서 빠진다(hard delete).
- 인덱스 없이 COUNT 로 센다. 축제 규모(수만 행 이하)에서는 문제없고, 자동 갱신이 없어 부하도 버튼 누를 때뿐이다.

응답 `data`:

| 필드 | 뜻 |
|---|---|
| `timezone`·`from`·`to`·`generatedAt` | `Asia/Seoul`, `2026-09-29`, `2026-10-01`, 집계 시각(UTC Instant) |
| `saju.results` | 사주 결과(`result`) 생성 수. `{total, days:[{date, count, hourly[24]}]}` — 이하 "시리즈" |
| `saju.byGender` | `MALE`·`FEMALE` |
| `compatibility.created` | 궁합(`compatibility`) 생성 시리즈 |
| `compatibility.byTier` | `GUIIN`·`CHALTTEOK`·`BEOT`·`SEUCHIM` |
| `dating.profiles` | 소개팅 프로필 등록 시리즈 |
| `dating.verifiedProfiles`·`deactivatedProfiles` | 그중 학교메일 인증 완료 / 지금 비활성 |
| `dating.recommendationViewers` | 기간 안에 추천 카드를 한 번이라도 받은 회원 수(중복 제거) |
| `dating.requests`·`requestsByStatus` | 소개팅 요청 시리즈, `PENDING`·`ACCEPTED`·`REJECTED`·`CANCELLED` |
| `dating.profilesByGender` | 기간 안에 등록한 프로필의 성비 |
| `dating.poolByGender` | **기간 무관** 지금 추천 풀(인증 완료·비활성 아님) 성비. 축제 전 사전 등록자도 추천에 나오므로 불균형은 풀 전체로 본다 |
| `dating.requestsBySenderGender` | 기간 안에 만든 요청을 보낸 쪽 성별로: `{sent, accepted, rejected, pending}` |
| `dating.accepted` | 매칭 성사 시리즈 — **수락 시각(`responded_at`)** 기준. 요청은 축제 전에 왔어도 기간 안에 수락되면 센다 |
| `dating.acceptedMembers` | 그 성사 건의 양쪽 프로필 수(중복 제거) |

분포 맵은 0 인 키도 항상 채운다. 수락률(수락 ÷ (수락+거절))은 페이지가 계산한다.
성별은 프로필에 없어서 회원의 사주 결과(`result.gender`, `member_id` 로 조인)에서 가져온다. 추천 로직과 같은 출처다. 결과가 없는 회원은 `UNKNOWN` 으로 세고, 그 키는 있을 때만 생긴다.

## 일괄 안내 메일 `/api/admin/notice-mails` (#159)

소개팅 프로필 보유자 전원(비활성 제외)에게 같은 메일을 한 번 보낸다. 2026-10-02 오전 정보 전달 메일용으로 만들었다.
관련 코드: `admin/AdminNoticeMailController`, `dating/DatingNoticeMailService`·`DatingNoticeMailScheduler`·`DatingNoticeMailRepository`·`DatingNoticeCampaignRepository`, V28 `dating_notice_mail`·`dating_notice_campaign`.
`admin.html` 에는 버튼이 없다 — curl 로만 쓴다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| POST | `/api/admin/notice-mails` | 바디 `{campaignKey, subject, body, mode, testTo, sendAt}`. 응답 `{campaignKey, mode, targets}` |
| GET | `/api/admin/notice-mails/{campaignKey}` | 진행 상황 `{campaignKey, targets, sent, failed, sending, state, sendAt, startedAt}` |
| DELETE | `/api/admin/notice-mails/{campaignKey}/schedule` | 시작 전 예약 취소. 없거나 이미 시작했으면 400 `INVALID_INPUT` |

- `mode`
  - `DRY_RUN`: 대상 수만 센다. 보내지도 기록하지도 않는다. 200
  - `TEST`: `testTo` 한 주소로만 보낸다(기록 없음). 실패하면 503 `MAIL_UNAVAILABLE`, `testTo` 가 없으면 400 `INVALID_INPUT`. 200
  - `SEND`: 대상을 세고 **202 로 바로 돌아온 뒤** 백그라운드로 보낸다(건당 200ms 간격). 결과는 GET 으로 본다
  - `SCHEDULE`: `sendAt`(오프셋 필수, 예: `2026-10-02T09:00:00+09:00`)에 보낸다. 200
    - 앱이 **1분마다** 시각이 지난 예약을 확인해 보낸다. 그래서 실제 시작은 `sendAt` 뒤 최대 1분이다
    - 그 시각에 앱이 재기동 중이었어도 뜬 뒤 다음 확인에서 보낸다. **1시간 넘게 늦었으면 보내지 않고** `state=EXPIRED` 로 둔다
    - 시작 전(`state=SCHEDULED`)에는 같은 키로 다시 `SCHEDULE` 해서 제목·본문·시각을 고친다. 시작 뒤에는 400
    - `sendAt` 이 없거나 지금보다 이전이면 400 `INVALID_INPUT` — 즉시 발송은 `SEND`
    - 발송 기록은 `SEND` 와 같은 표를 같은 키로 쓴다. 예약 발송에서 실패한 사람은 같은 키로 `SEND` 하면 그 사람에게만 간다
- 대상: `dating_profile.deactivated_at IS NULL` 전부. 수신 주소는 프로필의 학교메일
- `campaignKey`: 소문자·숫자·`-`, 50자 이하. **중복 방지 단위다.** 같은 키로 다시 `SEND` 하면 아직 안 간 사람·`FAILED` 인 사람에게만 간다. 연달아 두 번 눌러도 수신자마다 한 번
- 본문은 plain text 로 그대로 보낸다. 문구를 요청으로 받는 건 발송 직전 오타를 재배포 없이 고치고 `TEST` 로 먼저 받아 보기 위해서다
- **`sending` 이 0 이 아닌 채로 멈춰 있으면** 발송 도중 앱이 재기동된 것이다. 그 사람들은 실제로 나갔는지 알 수 없어 자동 재발송하지 않는다 — 필요하면 DB 에서 `status` 를 `FAILED` 로 바꾸고 같은 키로 `SEND`
- 발송 중에는 배포(`dev`·`main` push)하지 않는다. 120명 기준 1분 안쪽이다. 예약해 둔 뒤의 배포는 괜찮다(예약은 DB 에 있다)
- 정보 전달 메일 전용이다. 할인·이벤트 홍보가 섞이면 광고성 정보라 제목 `(광고)`·수신거부 방법이 필요해진다(정보통신망법 §50) — 이 API 는 그걸 지원하지 않는다

```bash
read -s ADMIN_TOKEN
H="X-Admin-Token: $ADMIN_TOKEN"; B=https://api.threadoffate.site/api/admin/notice-mails
J='Content-Type: application/json'
curl -s -X POST -H "$H" -H "$J" "$B" -d '{"campaignKey":"1002-notice","subject":"제목","body":"내용","mode":"DRY_RUN"}'
curl -s -X POST -H "$H" -H "$J" "$B" -d '{"campaignKey":"1002-notice","subject":"제목","body":"내용","mode":"TEST","testTo":"me@example.com"}'
curl -s -X POST -H "$H" -H "$J" "$B" -d '{"campaignKey":"1002-notice","subject":"제목","body":"내용","mode":"SCHEDULE","sendAt":"2026-10-02T09:00:00+09:00"}'
curl -s -H "$H" "$B/1002-notice"                      # state=SCHEDULED 확인
curl -s -X DELETE -H "$H" "$B/1002-notice/schedule"   # 취소
curl -s -X POST -H "$H" -H "$J" "$B" -d '{"campaignKey":"1002-notice","subject":"제목","body":"내용","mode":"SEND"}'   # 지금 보내기·실패자 재발송
```

본문 줄바꿈은 JSON 문자열 안에서 `\n`. 길면 `-d @mail.json` 으로 파일을 넘긴다(UTF-8 로 저장).

## 비활성화가 미치는 곳

`dating_profile.deactivated_at`(V26). `DatingProfile.isEligible()` 이 `verified_at IS NOT NULL AND deactivated_at IS NULL` 로 바뀌어 아래 셋에 한 번에 적용된다.

- 추천 풀(`findEligible`)에서 빠진다. 이미 떠 있는 카드는 그 조회자의 다음 조회에서 내려간다(`activeEligible`).
- 그 사람이 받은 PENDING 요청은 수락하면 409 `DATING_REQUEST_CONFLICT`.
- 본인은 `/api/dating/**` 를 계속 쓸 수 있다(요청 목록·이미 ACCEPTED 된 연락처 유지). 본인 추천 조회 차단은 넣지 않았다.

## 삭제가 지우는 것 / 남기는 것

- 지운다: `dating_profile` 행, 추천(후보로 등장한 행)·`dating_request`·`dating_email_verification`(FK cascade), 붙어 있던 `dating_photo` 행과 S3 원본·썸네일.
- 남긴다: `member`, `result`, `thread_ledger`(원장 불변), 그 회원이 **조회자**였던 추천 이력, 붙이지 않은 업로드 사진 행.
- 같은 회원이 다시 등록할 수 있다(코드 인증부터).
- **회원(`member`) 삭제 API 는 없다.** 원장까지 cascade 되므로 팀 결정 후 별도.

## 로그

`profileId`·`memberId` 만 남긴다. 이메일·수정 값·토큰은 안 남긴다. 인증 실패는 `admin auth failed. path=…, ip=…` warn.

## curl (페이지 없이 쓸 때)

```bash
read -s ADMIN_TOKEN   # 히스토리에 안 남게
H="X-Admin-Token: $ADMIN_TOKEN"; B=https://api.threadoffate.site/api/admin/dating/profiles
curl -s -H "$H" "$B?email=student@dgu.ac.kr"
curl -s -H "$H" https://api.threadoffate.site/api/admin/stats
curl -s -X POST -H "$H" "$B/<profileId>/deactivate"
curl -s -X PATCH -H "$H" -H 'Content-Type: application/json' -d '{"name":"새이름"}' "$B/<profileId>"
curl -s -X DELETE -H "$H" "$B/<profileId>"
```

사진 교체: `photo-upload-url` 응답의 `uploadUrl` 에 `curl -X PUT -H 'Content-Type: image/jpeg' --data-binary @a.jpg` → `PATCH …/photo` 에 `photoId`.

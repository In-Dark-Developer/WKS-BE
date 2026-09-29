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
curl -s -X POST -H "$H" "$B/<profileId>/deactivate"
curl -s -X PATCH -H "$H" -H 'Content-Type: application/json' -d '{"name":"새이름"}' "$B/<profileId>"
curl -s -X DELETE -H "$H" "$B/<profileId>"
```

사진 교체: `photo-upload-url` 응답의 `uploadUrl` 에 `curl -X PUT -H 'Content-Type: image/jpeg' --data-binary @a.jpg` → `PATCH …/photo` 에 `photoId`.

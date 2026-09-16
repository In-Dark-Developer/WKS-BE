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
| `main`·`dev` 브랜치 생성 + 보호 설정 | ✅ 생성. 보호 설정은 private 저장소 무료 플랜이라 불가 (PR 리뷰로 대체) |
| 배포 상태 | ✅ `dev` push → GitHub Actions → EC2 (https://api.threadoffate.site, nginx + certbot). `main` 배포는 미정 |
| `/api/health` (배포 도메인) | ✅ 200 (2026-09-15 확인) |
| Flyway 최신 버전 | V8 |
| api-spec 프론트 전달 | ❌ 미전달 |
| CORS localhost:3000 허용 | ✅ 기본값 (`CORS_ALLOWED_ORIGINS` 로 덮어씀). 프론트 배포 도메인은 미반영 |

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
| V8 | 곽도윤 | signup에 `photo_key` 컬럼 추가 (S3 사진 업로드, 선택값) | 구현 완료 |
| V7 | 곽도윤 | signup에 `name`·`contact_method`·`contact_value`·`department`·`mbti`·`bio` 컬럼 추가 | 구현 완료 |
| V6 | 최선우 | result `share_id` + 궁합 A↔B 무순서 유니크 인덱스 + guest 조회 인덱스 | 구현 완료 |
| V5 | 차은호 | reading 의 `destiny_title` 삭제 (조회 시 계산) | 완료 |
| V4 | 차은호 | reading 의 `lucky_item`·`lucky_place` 삭제 (조회 시 계산) | 완료 |
| V3 | 차은호 | reading 의 등급 컬럼을 0~100 점수 컬럼으로 교체 (`*_grade` → `*_score`) | 완료 |
| V2 | 최선우 | 운명·등급·행운 콘텐츠 저장을 위한 reading 확장 | 완료 |
| V1 | 곽도윤 | init schema (5개 테이블) | 완료 |

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
| 2026-09-13 | 본인용 `resultId`와 공개용 `shareId` 분리. `GET /api/shares/{shareId}`, 궁합 POST 추가 | ❌ |
| 2026-09-13 | `luckyItem`·`luckyPlace` 가 오늘 기준으로 매일 바뀜 (조회마다 재계산) | ❌ |
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

### 2026-09-16 (수) · 곽도윤 · signup/ 사진 업로드 S3 연동 (#54) · Claude Code

**한 일**
- 9/15에 "사진 업로드는 2차 릴리즈로 보류"로 결정했던 걸 번복 — 기획 변경으로 이번 릴리즈에 사진도 선택값으로 받기로 함
- 업로드 방식은 **presigned URL**로 결정 (서버가 파일 바이트를 직접 받지 않음): `POST /api/signups/photo-upload-url`로 S3 PUT용 presigned URL·`photoKey` 발급 → 프론트가 S3에 직접 업로드 → `photoKey`를 기존 `POST /api/signups` 요청에 실어 보냄
- `software.amazon.awssdk:s3`(BOM 2.29.52) 신규 의존성 추가 — **convention.md의 "AI가 새 라이브러리 추천하면 일단 거절, 팀에 물어본다" 규칙에 걸려서 진행 전에 사용자(곽도윤 본인) 확인 받음**
- `common/config/S3Config.java` 신규 — `S3Client`/`S3Presigner` 빈. 자격증명은 하드코딩하지 않고 AWS 기본 자격증명 체인(`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` 환경변수 또는 EC2 IAM 역할)에 위임. `S3Client`는 `apiCallTimeout(5s)` 명시 (외부 호출 타임아웃 필수 규칙)
- `signup/PhotoUploadService.java` 신규 — `createUploadUrl(contentType)`: `image/jpeg`·`image/png`·`image/webp`만 허용, 아니면 `INVALID_INPUT`. 키는 `signup-photos/{UUID}.{ext}`. `verifyPhotoExists(photoKey)`: `SignupService.createSignup`에서 호출 — `photoKey`가 실제 S3에 없으면(미업로드·만료·위조) `INVALID_INPUT` 400으로 막음 (resultId 검증과 동일 패턴)
- `Signup` 엔티티·`CreateSignupRequest`에 `photoKey` 추가 (마지막 파라미터로 추가해서 기존 호출부는 `null` 하나만 붙이면 되게 함). `V8__add_signup_photo_key.sql` — `photo_key VARCHAR(255)` nullable 컬럼
- `SignupServiceTest`·`SignupControllerTest` 기존 생성자 호출부 전부 갱신(11번째 인자 추가) + `PhotoUploadServiceTest` 신규 + 사진 검증 성공/실패 케이스 `SignupServiceTest`에 추가. `./gradlew test --tests "com.darkness.wks.signup.*"` 통과 확인
- 새 `ErrorCode`는 추가하지 않음 — `INVALID_INPUT` 재사용 (resend의 "이미 인증됨" 케이스와 같은 패턴)이라 `common/ErrorCode.java`는 안 건드림

**건드린 파일/패키지**
- `build.gradle` — AWS SDK BOM + s3 모듈 추가
- `common/config/S3Config.java` (신규)
- `signup/PhotoUploadService.java` (신규), `signup/PhotoUploadServiceTest.java` (신규)
- `signup/dto/PhotoUploadUrlRequest.java`, `signup/dto/PhotoUploadUrlResponse.java` (신규)
- `signup/entity/Signup.java`, `signup/dto/CreateSignupRequest.java`, `signup/SignupService.java`, `signup/SignupController.java`
- `signup/SignupServiceTest.java`, `signup/SignupControllerTest.java`
- `db/migration/V8__add_signup_photo_key.sql` (신규)
- `application.yml`(`app.aws.*`), `.env.prod.example`, `docker-compose.prod.yml` — `AWS_REGION`·`AWS_S3_BUCKET`·`AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY` 추가
- `api.md` §4, `docs/api-spec.md` §5, `docs/backend-requirements.md` FR-SU-16, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- **실제 S3 버킷·IAM 자격증명이 아직 없다.** `AWS_S3_BUCKET`이 비어있으면(현재 기본값) 이 기능은 호출 시 에러난다 — 배포 전에 버킷 생성 + IAM 사용자(또는 EC2 역할) 발급 + `.env` 채우기 필요. 로컬에서 이 엔드포인트 테스트하려면 각자 로컬에 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_S3_BUCKET` 환경변수 설정 필요 (`application-local.yml.example`엔 안 넣었음 — 필수 아니라 기동엔 안 막힘)
- 버킷 CORS 설정도 필요함 (프론트가 브라우저에서 presigned URL로 직접 PUT 하려면 S3 버킷에 프론트 origin CORS 허용 필요) — **아직 안 함, 배포 전 확인**
- **서버가 업로드 용량 제한을 강제하지 않는다.** presigned PUT은 POST policy와 달리 `content-length-range` 조건을 못 건다 — 프론트가 파일 선택 시 용량 제한하거나, S3 버킷 정책/Lambda로 후처리 검증 필요 (FR-SU-16에 명시)
- `photoKey`는 선택값. 안 보내면 사진 없이 신청 가능 (기존 6개 프로필 필드와 동일하게 전부 nullable 기조 유지)
- presigned URL 만료는 10분(`app.aws.s3.presigned-url-ttl-minutes`) — 프론트가 URL 발급 후 오래 끌다 업로드하면 만료돼서 S3가 거부함. 재발급은 그냥 엔드포인트 재호출

**막힌 것 / 넘기는 것**
- S3 버킷 생성·IAM 자격증명 발급·버킷 CORS 설정 — AWS 콘솔 작업이라 코드로 대신 못 함. 배포 전 필수
- 사진 용량 상한이 기획 미정 — 정해지면 프론트 검증 로직에 반영 필요

**문서 변경**
- `api.md` §4, `docs/api-spec.md` §5, `docs/backend-requirements.md` FR-SU-16, `docs/handoff.md`

**프론트에 알려야 할 것**
- `POST /api/signups/photo-upload-url` 신규 — `{ contentType }` → `{ uploadUrl, photoKey, expiresInSeconds }`. `uploadUrl`에 파일을 `PUT`(Content-Type 헤더 동일하게) → `photoKey`를 `POST /api/signups`의 `photoKey` 필드에 실어 보내면 됨
- `photoKey`는 선택값 — 사진 없이도 신청 가능
- `api.md` §4 갱신함

### 2026-09-14 (월) · 차은호 · saju/ 등급 노출 완화 (#32) · Codex

**한 일**
- 해석 문장에 내부 운세 등급을 반드시 명시하지 않아도 되도록 시스템 프롬프트 규칙을 명확히 함
- 등급은 기존처럼 해석의 방향과 강도를 정하는 입력으로 유지

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- API 응답 구조와 등급 산출 로직은 바뀌지 않음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 곽도윤 · 사전신청 API 필드 확장 (피그마 반영) · Claude Code

**한 일**
- 프론트가 공유한 사전신청 화면(피그마, "가을 축제, 나에게 어떤 인연이 찾아올까?")과 `POST /api/signups` 계약을 대조 → 이름·연락처(전화/인스타)·학과·MBTI·자기소개가 API에 없는 걸 확인
- 9/13 기록에 "팀 결정 필요"로 남아있던 FR-SU-11("이름·전화번호 미수집")을 **정책 변경**으로 해결 — 피그마대로 수집하는 쪽으로 결정
- `Signup` 엔티티·`CreateSignupRequest`에 `name`·`contactMethod`(`PHONE`\|`INSTAGRAM`)·`contactValue`·`department`·`mbti`·`bio` 추가. `contactMethod=PHONE`일 때만 휴대폰 번호 형식 검증(`SignupService.validateContact`), `INSTAGRAM`은 형식 제약 없음
- `common/ContactMethod` enum 신규 추가 (`Gender`와 동일 패턴)
- `V7__add_signup_profile_fields.sql` 작성 — signup에 6개 컬럼 추가
- **(정정, 같은 날)** 공유해주신 피그마가 사전신청 화면 **전체**이고 "이 화면 안에서 뭘 필수/선택으로 할지는 아직 안 정해졌다"는 사용자 확인을 받음. 처음엔 6개 필드를 전부 필수로 구현했었는데, 이 확인 후 **전부 nullable로 되돌림** — DB 컬럼 `NOT NULL` 제거, DTO `@NotBlank`/`@NotNull` 제거(형식 검증(`@Size`/`@Pattern`/전화번호 정규식)은 값이 있을 때만 동작), 서비스에 빈 문자열→`null` 정규화(`SignupService.blankToNull`) 추가
- `docs/api-spec.md` §5, `api.md` §4, `docs/backend-requirements.md` FR-SU 섹션 갱신 (필수 표시를 전부 "⚠️ 미정"으로)
- `SignupServiceTest`·`SignupControllerTest`의 기존 생성자 호출부 전부 갱신 + `PHONE` 형식 오류·`INSTAGRAM` 허용·6개 필드 전부 `null`이어도 생성되는 케이스 테스트 추가. `./gradlew test --tests "com.darkness.wks.signup.*"` 통과 확인

**건드린 파일/패키지**
- `signup/entity/Signup.java`, `signup/dto/CreateSignupRequest.java`, `signup/SignupService.java`, `signup/SignupController.java`
- `common/ContactMethod.java` (신규)
- `db/migration/V7__add_signup_profile_fields.sql` (신규)
- `signup/SignupServiceTest.java`, `signup/SignupControllerTest.java`
- `docs/api-spec.md`, `api.md`, `docs/backend-requirements.md`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- **6개 신규 필드(`name`/`contactMethod`/`contactValue`/`department`/`mbti`/`bio`) 전부 현재 선택값(nullable)이다.** 필수/선택은 기획 미확정 — 확정되면 V7 후속 마이그레이션으로 `NOT NULL` 추가 + DTO에 `@NotBlank`/`@NotNull` 추가 필요. **이 상태로 배포하면 누구든 이름·연락처 없이 신청 가능** — 이대로 MVP 낼지 미리 확인
- **사진 업로드는 이번 릴리즈에 안 넣었다** (사용자 결정, 2026-09-15). S3 등 스토리지 연동 필요해서 리스크가 커서 2차로 보류. 이미지 저장 방식(S3 vs EC2 로컬 디스크 vs DB)은 아직 미정 — 2차 착수 전 결정 필요. 프론트에 사진 필드는 UI만 두거나 비활성화하라고 전달 필요 (FR-SU-16)
- **`gender`/`preferGender`는 그대로 유지했다.** 공유받은 피그마가 사전신청 화면 전체인 게 확인됐는데, 그 화면엔 성별 입력이 안 보인다. 즉 **현재 API는 화면에 없는 필드를 필수로 요구 중** — 프론트가 이 두 필드를 어디서/어떻게 보낼지 확인 필요. 매칭(FR-CP 궁합) 로직이 성별에 의존해서 임의로 빼지 않았음
- **`나이`는 이번 API에 안 넣었다.** 9/13 기록(FR-10)엔 나이도 Must였는데 공유된 피그마 화면엔 없어서 뺐다. 프론트가 다른 화면에서 받는지 확인 필요
- MBTI는 정규식(`^[EI][SN][TF][JP]$`, 빈 문자열은 허용)만 검증, 자기소개 글자수 상한 500자는 **피그마에 실제 숫자가 없어서 임의로 잡은 값** — 기획 확정되면 `CreateSignupRequest.bio`의 `@Size(max=...)`와 V7 컬럼 길이 같이 조정

**막힌 것 / 넘기는 것**
- 프론트·기획 확인 필요: (1) 6개 필드 중 필수는 어느 것인지, (2) 사진 저장 방식(S3/로컬디스크/기타) 및 2차 착수 시점, (3) 성별/선호성별 입력 위치, (4) 나이 필드 포함 여부·자기소개 최대 글자수

**문서 변경**
- `docs/api-spec.md`, `api.md`, `docs/backend-requirements.md`, `docs/handoff.md`

**프론트에 알려야 할 것**
- `POST /api/signups`에 `name`·`contactMethod`·`contactValue`·`department`·`mbti`·`bio` 6개 필드 추가됨. **전부 현재는 선택값** — 필수 여부 확정되면 재공지 예정. `api.md` §4 갱신함
- 사진 업로드는 이번 API에 없음 — 2차 릴리즈 예정
- 성별/나이 관련 위 미결정 항목 답변 필요

### 2026-09-14 (월) · 곽도윤 · 프론트 공유용 api.md 작성 · Claude Code

**한 일**
- 루트에 `api.md` 신규 작성 — 프론트 팀 전달 목적. 구현된 8개 엔드포인트(health/results×2/shares×2/compatibility/signups×3) 전부 실제 DTO 코드 대조해서 정리
- Base URL 섹션에 운영 도메인(`https://api.threadoffate.site`)·로컬(`localhost:8080`) 둘 다 명시

**건드린 파일/패키지**
- `api.md` (신규, 루트)

**다음 사람이 알아야 할 것**
- `docs/api-spec.md`가 여전히 내부 원본이고 `api.md`는 그걸 프론트 공유용으로 옮겨 적은 것. **스펙 바뀌면 `docs/api-spec.md` 먼저 고치고 `api.md`도 같이 갱신할 것** — 안 그러면 두 문서가 어긋남
- `api.md`에 이메일 도메인 화이트리스트가 "현재 검증 건너뛰는 중"이라고 명시해둠 — 팀 결정되면 이 문구도 지울 것

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `api.md` 신규 작성

**프론트에 알려야 할 것**
- `api.md` 파일로 전체 API 명세 전달함 (기존 `docs/api-spec.md` 미전달 상태였던 것 참고)

### 2026-09-14 (월) · 곽도윤 · CI/CD 배포 실패 원인 조사 · Claude Code

**한 일**
- EC2 배포 시 `appleboy/scp-action`이 간헐적으로 `Process exited with status 1`로 실패하는 문제 조사
- **원인: EC2 루트 파티션(`/`, 6.7G) 디스크 풀.** `df -h` 확인 결과 `Use% 100%`, 여유 0
- 근본 원인: `deploy.yml` 마지막 줄이 `docker image prune -f`(태그 없는 이미지만 정리)였는데, 매 배포가 `github.sha`로 유니크 태그를 붙여 pull하기 때문에 이전 커밋 이미지들이 태그가 남아 절대 안 지워지고 계속 쌓임
- `nginx/default.conf`가 디렉터리로 잘못 생성됐을 가능성도 확인했으나 아니었음 (정상 파일, `ubuntu` 소유)

**건드린 파일/패키지**
- `.github/workflows/deploy.yml` — `docker image prune -f` → `docker image prune -a -f`, `concurrency` 그룹 추가(동시 배포 방지, 원인은 아니었지만 안전장치로 유지), scp 스텝 `debug: true` 추가

**다음 사람이 알아야 할 것**
- **CI에서 디스크 정리는 고쳤지만, 지금 당장 EC2가 꽉 찬 상태는 수동으로 풀어야 동작한다.** SSH로 들어가서:
  ```
  docker image prune -a -f
  sudo journalctl --vacuum-size=200M
  ```
  (`--volumes`는 절대 쓰지 말 것 — postgres 데이터·certbot 인증서가 named volume에 있어서 같이 삭제됨)
- 루트 파티션이 6.7G로 상당히 작다. 이미지가 수백MB 단위라 몇 번만 더 쌓여도 다시 찰 수 있음 — 볼륨 확장이나 별도 데이터 디스크 분리를 팀에서 검토할 것

**막힌 것 / 넘기는 것**
- EC2 디스크 수동 정리는 아직 실행 안 함 (서버 접근 권한이 있는 사람이 위 명령 실행 필요)

**문서 변경**
- 없음

**프론트에 알려야 할 것**
- 없음

### 2026-09-14 (월) · 곽도윤 · signup/ 사전등록·이메일 인증 (§5) · Claude Code

**한 일**
- `POST /api/signups`, `POST /api/signups/resend`, `GET /api/signups/verify` 3개 엔드포인트 구현 (기존엔 TODO 스텁만 있었음)
- `EmailVerificationService.sendVerificationEmail()` 구현 (기존엔 `UnsupportedOperationException`)
- `SignupController` 매핑을 `/api/signup` → `/api/signups` 로 수정 (api-spec §7 복수형 규칙 위반이었음)
- `Signup` 엔티티에 `issueCoupon()`, `markVerified()`, `isVerified()` 추가 (Setter 금지 규칙 준수)
- `EmailVerificationService.verify()` 반환 타입을 `void` → `EmailVerification` 으로 변경 (호출부에서 `signup.markVerified()` 하기 위함)
- `GlobalExceptionHandler` 에 `MissingServletRequestParameterException` 핸들러 추가 (400 `INVALID_INPUT`). `GET /verify?token=` 처럼 필수 쿼리 파라미터가 없으면 기존엔 500으로 새던 걸 공통으로 막음 — signup 외 다른 패키지에도 적용되는 공통 인프라 변경이라 공유함
- Swagger 문서화: signup 3개 엔드포인트에 `@Operation`/`@ApiResponses`/`@Schema` 전부 붙임 (다른 컨트롤러와 동일 수준). `HealthController` 에 빠져있던 `@Tag` 도 추가함 (전 컨트롤러 통일)
- 로컬에서 실제 기동해 `/v3/api-docs` 확인함 — 8개 경로(health/results×2/shares×2/signups×3) 전부 정상 노출, 요청·응답 스키마와 에러 예시까지 반영됨 확인. 기존에 8080에서 돌고 있던 다른 프로세스(PID 11544, 9/11부터 떠있던 것으로 보임)는 건드리지 않고 8090으로 별도 확인 후 종료함

**건드린 파일/패키지**
- `signup/` 전체 (Controller, Service, EmailVerificationService, Repository, Entity, dto/)
- `application.yml`, `.env.prod.example`, `docker-compose.prod.yml` — `BACKEND_BASE_URL` 신규 추가 (인증 메일 링크가 백엔드 `/api/signups/verify` 를 가리켜야 해서 필요)
- `common/exception/GlobalExceptionHandler.java`, `common/HealthController.java` — 공통 인프라 소폭 수정 (위 참고)

**다음 사람이 알아야 할 것**
- **이메일 도메인 화이트리스트 미결정 상태를 그대로 반영**: `SIGNUP_ALLOWED_EMAIL_DOMAINS` 가 비어있으면(현재 로컬/prod 기본값) 도메인 검증을 **건너뛴다** (전체 허용). 학교 도메인이 정해지면 그 값만 채우면 동작함 — 코드 변경 불필요
- **메일 제목/본문은 임시 문구.** `api-spec.md` 의 `destiny.title` 처럼 기획(영채) 확정 전까지 자리만 채움 (`EmailVerificationService.SUBJECT`, 본문 텍스트 블록)
- **`resultId` 가 존재하지 않을 때 정책을 `RESULT_NOT_FOUND` 404 로 임시 결정함.** `backend-requirements.md` §10 인수조건에 "Day 3까지 결정" 이라고 되어 있던 항목 — 팀 확정되면 바꿀 것
- 재발송(`/resend`)의 "이미 인증됨" 케이스는 전용 에러코드가 없어 `INVALID_INPUT` 400 재사용함. api-spec에 명시된 에러코드 표에 없는 케이스라 전용 코드가 필요하면 팀 논의 후 추가
- 쿠폰은 이메일 1개당 신청 1건(unique 제약)이라 신청 성공 시 항상 `couponIssued: true` 로 처리함 (별도 쿠폰 테이블 없음, FR-SU-10 "1인 1회"는 이메일 unique 제약으로 자연히 보장)

**막힌 것 / 넘기는 것**
- SMTP 발송 계정 미확보 상태라 로컬에서 실제 메일 발송 확인은 못 했음 (todo.md 대로 본인 계정으로 임시 테스트 필요)
- 학교 웹메일 도메인 화이트리스트, 축제 D-day 등은 여전히 미정 (todo.md §3 그대로)

**문서 변경**
- `api-spec.md` §5 `POST /api/signups/resend` 에 Response 200 예시 추가 (기존엔 없었음). 에러코드도 `INVALID_INPUT` 재사용임을 명시

**프론트에 알려야 할 것**
- `resend` 응답 바디 형식: `{ "success": true, "data": { "mailSent": true, "message": "..." } }` (api-spec.md §5 에 추가함, 기존엔 예시 없었음)

### 2026-09-15 (화) · 차은호 · saju/ 운명 제목 기획 문구 반영 (#52) · Claude Code

**한 일**
- `destiny-titles.txt` 8개를 기획(영채) 피드백 1차 문구로 교체. 임시값 제거
- 매핑(연애·결혼·자녀): 상상상 복이 겹친 운명 / 상상하 오래 사랑할 운명 / 상하상 사랑을 남길 운명 / 상하하 사랑이 깊은 운명 / 하상상 가정을 이룰 운명 / 하상하 끝내 닿는 운명 / 하하상 복을 잇는 운명 / 하하하 제 길을 갈 운명

**건드린 파일/패키지**
- `resources/destiny-titles.txt`, `DestinyTitleTest`, `docs/api-spec.md` 예시, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- "피드백 1차"라 2차 오면 파일만 다시 교체. 코드 변경 없음
- 화면이 "당신의 운명은" + 제목이라 "…운명"이 겹침("당신의 운명은 사랑이 깊은 운명"). 기획에 전달함

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2 예시, `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (스키마 동일)

### 2026-09-15 (화) · 차은호 · saju/ 프롬프트 오행 역할 설명 (#50) · Claude Code

**한 일**
- #49 이후 오행 사실은 맞지만 카드가 "흙인 당신 → 결혼은 물과 불 → 자녀는 나무"로 읽혀 독자에게 불일치로 보임. "라벨 그대로 읊기 금지"가 역할 언급까지 지운 것
- 규칙 교체: 나의 기운이 아닌 기운은 "흙의 기운인 당신에게 물의 기운은 배우자 인연을 뜻해요"처럼 뜻을 꼭 밝힌다. 각 항목 첫 문장은 나의 기운에서 시작. 연애운 기운 지정(나의 기운 + 배우자 기운) 추가
- 실호출 3회(카드와 같은 흙 일간·남성 구성): 연애·결혼·자녀 첫 문장 전부 "흙의 기운인 당신에게 X의 기운은 …을 뜻해요". 합쇼체 0. 입력 1,474토큰(+130)

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 없음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 차은호 · saju/ 프롬프트에 오행 사실 전달 (#48) · Claude Code

**한 일**
- 같은 입력인데 시도마다 언급 오행이 달랐음(첫 시도 "물과 불", 두 번째 "물과 흙"). 프롬프트에 팔자 글자만 있어 LLM이 오행을 스스로 골랐기 때문
- `ReadingGenerator.buildPrompt`: 나의 기운(일간), 강한 기운(세력 상위 2), 약한 기운(총량 10% 미만), 배우자 기운(성별 기준 배우자성)·배우자 자리의 기운(일지), 자녀 기운(자녀성)을 나무·불·흙·쇠·물로 전달
- 시스템 프롬프트: 기운 이야기는 전달된 오행 정보만 근거. 항목별로 어느 기운에서 끌어낼지 지정. 라벨("배우자 기운" 등) 그대로 읊기 금지. 합쇼체 금지 명시
- 스모크 5회(3.5-flash-lite): 결혼은 항상 나무+불, 자녀는 항상 불, 성격은 쇠+나무·물. 토큰 약 2,000 (+50)

**건드린 파일/패키지**
- `saju/ReadingGenerator.java`, `resources/prompts/reading-system.txt`, `ReadingGeneratorTest`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 합쇼체("~합니다")가 2회 중 1회꼴로 섞임. 프롬프트 금지 문구로는 완전히 안 잡힘. 거슬리면 temperature 0.9 → 0.7 검토
- 프롬프트가 등급 외 사실을 더 받으므로 `ReadingGeneratorTest` 의 기대 문자열이 오행 계산(`Element.strengths`)에 묶임. 가중치 바꾸면 테스트 문자열도 갱신

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 차은호 · saju/ 프롬프트 '등급' 표현 제거 (#46) · Claude Code

**한 일**
- 해석 문장에 "…만드는 등급입니다"가 나옴. 출력 지시 3곳이 "이 등급이 나온 기운"이라 LLM이 따라 씀
- 원칙에 "'등급'이라는 말과 등급 기호(SS, A+ 등)를 문장에 쓰지 않는다. '~하는 사주예요', '~하는 흐름이에요'처럼 풀어 쓴다" 추가. 출력 지시는 "이 운의 바탕이 되는 기운"으로
- 스모크(3.5-flash-lite) 1회: '등급' 0회, 등급 기호 0회. 5.5초, 1,953토큰

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 없음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-15 (화) · 차은호 · result/ 행운 아이템 해시 키 (#40) + 장소 매일 변경 (#42) · Claude Code

**한 일**
- `ResultResponse.from`: 아이템 해시 키를 `resultId` → `생년월일/시간/성별`. 같은 입력을 다시 생성해도 같은 날엔 같은 아이템. 날짜 바뀌면 변경(기존과 동일)
- `LuckyPlace.of(pillars, today)`: 장소도 매일 변경. 보완 오행은 원국 기준 그대로 고정, 그 오행 풀(3~4곳) 안에서 `팔자 + 날짜` 해시. #34 의 "사람마다 고정"은 폐기
- 아이템·장소 선택 해시를 `LuckyPool.pick` 으로 통일. `String.hashCode()` 는 날짜 끝자리에 선형이라 매일 인덱스가 +1 되어 풀을 목록 순서대로 도는 주기(장소 4일·아이템 10일)가 있었음. `SplittableRandom(seed)` 로 섞어 제거. 결정적(같은 키 = 같은 결과), JVM 무관

**건드린 파일/패키지**
- `result/dto/ResultResponse.java`(3줄, 최선우 리뷰), `saju/LuckyPlace.java`, `saju/DailyLucky.java` 주석, `LuckyPlaceTest`, `docs/api-spec.md`, `docs/architecture.md`

**다음 사람이 알아야 할 것**
- 같은 생년월일·시간·성별인 두 사람은 같은 날 같은 아이템을 받는다 (의도)

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2, `docs/architecture.md` DDL 주석, `docs/handoff.md`

**프론트에 알려야 할 것**
- `luckyPlace` 도 매일 바뀐다 (#34 공지 번복). 오늘 결과와 내일 결과가 다를 수 있음

### 2026-09-15 (화) · 차은호 · saju/ + result/ 성별 반영 (#36) · Claude Code

**한 일**
- `ReadingScorer.score(pillars, gender)`: 배우자성·자녀성을 성별로 결정. 남자 재성=배우자·관성=자녀, 여자 관성=배우자·식상=자녀. 일지 보너스는 배우자성 30, 재·관 중 나머지 18. 시주 자녀 보너스는 자녀성과의 생극 관계로 통일
- `ReadingGenerator.generate(pillars, grades, gender)`: 프롬프트 첫 줄에 성별. 시스템 프롬프트에 "역할·호칭은 성별에 맞춘다, 어긋나는 표현 금지" 규칙 추가
- `ResultAnalysisPort.analyze(date, time, gender)` 시그니처 변경, `ResultService`·`SajuResultAnalysisAdapter` 호출부 반영 — 최선우 리뷰
- 보정 상수 재측정 (시주 포함 고유 팔자 265,004개): 결혼 63.0/0.93, 자녀 51.9/0.78, 연애 54.1/0.95. 남녀 원점수 중앙값 차이 0.5 이내라 공통 상수
- 시주 모름이면 자녀 시주 보너스를 0 대신 모집단 평균 13.2로 채움. 이전엔 시간 미입력자 자녀 상 비율 24%(입력자 52%)로 한 등급 손해 보던 것 → 51%로 복귀

**건드린 파일/패키지**
- `saju/ReadingScorer.java`, `saju/ReadingGenerator.java`, `resources/prompts/reading-system.txt`
- `result/ResultAnalysisPort.java`, `result/ResultService.java`(1줄), `result/SajuResultAnalysisAdapter.java`
- 테스트 5개, `docs/api-spec.md`, `docs/architecture.md`

**다음 사람이 알아야 할 것**
- 등급 분포(남녀 거의 동일): SS 6~9% / S 17~20% / A+ 25~26% / A 24~27% / B+ 18~20% / B 2~5%. 상(A+ 이상) 약 52%
- 같은 팔자라도 성별이 다르면 결혼·자녀 점수가 달라진다. 연애는 배우자성 비중만 달라져 차이 작음

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md` §2 gender 설명, `docs/architecture.md` §6, `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (요청·응답 스키마 변경 없음)

### 2026-09-14 (월) · 차은호 · saju/ + result/ 기능명세서 정렬 (#34) · Claude Code

**한 일**
- 운명 8유형 키 순서를 기능명세서와 동일하게 연애→결혼→자녀로 변경 (`destiny-titles.txt` 유형 1~8 재배열, `DestinyTitle.of`)
- 행운 아이템 오행을 명세 점수화로 교체: 사용자 궁합(십성) 40% + 오늘 일진 활성도 60%, 동점은 활성도→인성→비겁. 아이템은 `resultId + 날짜 + 오행` 해시 → 같은 팔자라도 사람마다 다름
- 행운 장소를 별도 로직으로 분리 (`LuckyPlace`): 원국 오행 세력 + 신강/신약(돕는 세력 40%/55% 컷)으로 보완 오행 → 장소. 사람마다 고정, 날짜 무관
- `Element` 에 `generates`/`controls`/`roleFor`/`strengths` 추가, `luckyAgainst` 제거. 풀 로더 `LuckyPool` 공용화
- `ResultResponse.from` 만 수정 (`DailyLucky.of(pillars, today, resultId)`, `LuckyPlace.of(pillars)`) — 최선우 리뷰

**건드린 파일/패키지**
- `saju/Element.java`, `saju/DailyLucky.java`, `saju/LuckyPlace.java`(신규), `saju/LuckyPool.java`(신규), `saju/DestinyTitle.java`, `resources/destiny-titles.txt`
- `result/dto/ResultResponse.java` (호출부 2줄)
- 테스트 4개, `docs/api-spec.md`, `docs/architecture.md`

**다음 사람이 알아야 할 것**
- 장소 오행 계산은 명세의 지장간·통근·월령 보정 생략 (기둥 가중치 0.7/1.3/1.0/0.9, 지지 0.85 만). 정밀화 요청 오면 `Element.strengths` 만 손대면 됨
- 운명 제목 문구는 여전히 임시값. 기획(영채) 8개 확정되면 `destiny-titles.txt` 만 교체

**막힌 것 / 넘기는 것**
- SS 추가 조건(FR-3) 도입 여부 → 기획 답 대기

**문서 변경**
- `docs/api-spec.md` §2 (luckyItem 매일·luckyPlace 고정, 운명 유형 순서), `docs/architecture.md` §6·DDL 주석, `docs/handoff.md`

**프론트에 알려야 할 것**
- `luckyPlace` 는 이제 사람마다 고정값 (매일 바뀌지 않음). `luckyItem` 은 매일 변경 유지

### 2026-09-14 (월) · 차은호 · saju/ 프롬프트 구체화 (#30) · Claude Code

**한 일**
- 해석 프롬프트에 원칙 추가: "기운 → 성격·행동으로 드러나는 모습 → 앞으로 흐름" 순서, 바넘 문장 금지, 근거 강도별 단정/가능성 구분, 낮은 등급은 약점 명시 후 대처법
- 항목별 필수 요소: 결혼(배우자 성향·시기 경향·결혼생활 분위기), 자녀(자녀 성향·관계 분위기·양육 주의점), 연애(끌리는 상대·반복 패턴·잘 맞는/조심할 사람)
- 스모크(3.5-flash-lite): 5.5초, 1,838토큰(이전 1,376). 문장이 "기운 좋아요 → 좋은 인연 와요" 식에서 "쇠·물 기운 → 마음 문 늦게 엶 → 이런 상대와 맞음" 식으로 구체화됨

**건드린 파일/패키지**
- `resources/prompts/reading-system.txt`, `docs/handoff.md`

**다음 사람이 알아야 할 것**
- 출력 토큰 약 30% 증가 → 건당 비용 약 6원(유료 시), 지연 +1초. 무료 lite 한도엔 영향 없음
- 전문 역술 프롬프트의 대운·세운·건강·금전·격국·용신 항목은 범위 밖·입력 부재라 반영 안 함

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-13 (일) · 최선우 · compatibility/ 분포 보정 · Codex

**한 일**
- 실제 팔자 10만 무작위 조합을 측정해 기존 분포(귀인 0.25%·찰떡 9.94%·벗 40.11%·스침 49.70%) 확인
- 관계 계산과 순서를 유지하는 단조 구간 보정으로 목표 분포 20%·30%·30%·20% 적용
- 고정 시드 실제 팔자 분포 회귀 테스트 추가(각 목표 ±2%p)

**다음 사람이 알아야 할 것**
- 기존 DB에 저장된 궁합은 재계산하지 않으며 새로 생성하는 궁합부터 보정 점수를 사용함

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/architecture.md`, `docs/backend-requirements.md`, `docs/handoff.md`

**프론트에 알려야 할 것**
- API 구조 변경 없음

### 2026-09-13 (일) · 최선우 · compatibility/ · Codex

**한 일**
- 결과 생성 시 본인용 `resultId`와 공개 링크용 `shareId`를 함께 발급
- `GET /api/shares/{shareId}` 공개 조회와 `POST /api/shares/{shareId}/compatibility` 구현
- A의 `CompatibilityCalculator`는 호출만 하고 점수 공식은 수정하지 않음
- 신규 조합 201, 기존 정·역방향 조합 200 및 재계산 방지
- 점수 Tier 경계(90/75/61), 자기 궁합·UUID·없는 결과 예외 처리
- Result 행 잠금과 V6 무순서 유니크 인덱스로 동시·역방향 중복 방지

**건드린 파일/패키지**
- `compatibility/` Controller, Service, Repository, DTO, Tier와 테스트
- `result/` 공유 Controller·응답·Repository·ResultResponse, `db/migration/V6__add_result_share_id_and_unique_compatibility_pair.sql`

**다음 사람이 알아야 할 것**
- 친구 결과는 기존 `POST /api/results`로 만들고, 궁합 생성 후 `GET /api/shares/{shareId}`를 재조회하면 전체 친구 목록이 최신순으로 보임
- 공유 조회 응답은 내부 `resultId`와 `shareId`를 노출하지 않음
- 궁합 계산기는 A 담당 계약이므로 API 테스트에서는 mock 점수만 사용함

**막힌 것 / 넘기는 것**
- 로컬 `application-local.yml`의 DB 비밀번호 불일치로 실제 PostgreSQL 기동 검증은 못 했고 전체 단위 테스트는 통과

**문서 변경**
- `docs/api-spec.md`, `docs/architecture.md`, `docs/backend-requirements.md`, `docs/handoff.md` 갱신

**프론트에 알려야 할 것**
- path는 링크 주인의 `shareId`, body는 친구의 `guestResultId`. 신규 201, 기존 조합 200

### 2026-09-13 (일) · 차은호 · saju/ + result/ 등급 컷·운명 제목 (#17) · Claude Code

**한 일**
- 등급 컷을 기획 확정값으로: SS 94 / S 84 / A+ 74 / A 64 / B+ 52 (`Grade.of`)
- `ReadingScorer` 에 기둥 가중치(년 0.7·월 1.3·일 1.0·시 0.9, 지지 0.85) 적용 → 십성 합이 소수가 되어 점수 계단이 줄어듦 (점수 종류 결혼 57→66, 자녀 26→43, 연애 39→59). 이어서 카테고리별 선형 보정(중앙값 74, 표준편차 약 14). 자녀 식에 시주 천간 관계 보너스와 인성 감점을 추가해 재료를 늘림(같은 점수에 9% 몰리던 것이 3~4% 이하로, 100점 잘림 9.5%→5.6%). 1950~2010 고유 팔자 8,225개 기준 분포: 보정 후 양 끝은 잘라내지 않고 90~100·0~10 구간에 지수적으로 눌러 펼침(100점 몰림 0%). 결혼 SS 5%·B 5%, 자녀 SS 9%·B 2%, 연애 SS 6%·B 4%. 상/하 각 약 50/50, 운명 8조합 각 8~19%. 분포 그래프는 `~/Workspace/WKS-BE-analysis/score-distribution.html` (레포 밖)
- 운명 제목 8종을 코드 표로: `DestinyTitle.of(m, c, l)` — 결혼·자녀·연애 상(A+ 이상)/하 조합. 제목은 `resources/destiny-titles.txt` (**임시값, 영채 확정 후 교체**)
- `destinyTitle` 을 Gemini 스키마에서 제거. `reading.destiny_title` 삭제(V5). 조회 시 점수로 계산
- 결혼·자녀·연애 문장을 기획 요구대로 **8~10문장**(문장마다 `\n`)으로. 스모크: 약 450자/항목, 9.2초, 1,432토큰/건 (이전 3초·850토큰). 30초 SLA 안. 비용 약 3원/건
- 읽기 쉽게 프롬프트 조정: 짧은 문장, 사주 용어·팔자 글자 금지, 어미 다양화, 축제 행동 조언 제외(기획)
- **기본 모델을 `gemini-3.5-flash-lite` 로 변경.** 같은 무료 키로 3.6-flash 는 하루 20건인데 3.5-flash-lite 는 2,000회 연속 성공(429 없음, 약 85 RPM). 품질 스모크 4.6초·1,376토큰, 문장 수준 동등. 실제 크기 요청의 TPM 한도는 미확인 → Day 6 에 실제 크기로 재측정
- **무료 티어 한도 실측 (Day 6 항목 조기 확인)**: `429 RESOURCE_EXHAUSTED ... generate_content_free_tier_requests, limit: 20, model: gemini-3.6-flash`. 오늘 호출 약 18건 만에 막힘 → **무료 티어는 하루 20건 수준**. 축제 트래픽 불가. 결제 연결(Tier 1) 필수 — 곽도윤. 429 는 재시도 안 하도록 수정
- 테스트 54건

**건드린 파일/패키지**
- `saju/`: `Grade`, `ReadingScorer`, `DestinyTitle`(신규), `Reading`, `ReadingGenerator`, 프롬프트, `destiny-titles.txt`
- `result/`(최선우 리뷰): `ResultAnalysisPort`(`Destiny` 레코드 제거 → `destinyDescription`), 어댑터 2개, `ResultService`, `entity/Reading`, `dto/ResultResponse`, V5

**다음 사람이 알아야 할 것**
- 이제 저장되는 해석 텍스트는 운명 설명 + 결혼·자녀·연애 문장 4개뿐. 제목·등급·행운은 전부 점수·날짜에서 계산
- 등급 컷이나 제목 문구를 바꿔도 마이그레이션 없음. `Grade.of` 또는 `destiny-titles.txt` 만
- 가중치를 바꾸면 보정 상수(`calibrate` 의 중앙값·scale)도 다시 잰다. 측정 스크립트는 scratch 라 없음 — 그리드로 원점수 중앙값·표준편차 재계산하면 됨

**막힌 것 / 넘기는 것**
- 영채: 운명 제목 8개 문구
- 최선우: `result/` 변경 리뷰 (PR 은 #15 위에 쌓임)

**문서 변경**
- `docs/api-spec.md` §2 (등급 컷·제목 8종), `docs/architecture.md` §5·§6, `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (필드 구조 동일)

### 2026-09-13 (일) · 차은호 · saju/ + result/ 오늘의 행운 (#14) · Claude Code

**한 일**
- 행운 아이템·장소를 LLM 에서 빼고 코드로: `DailyLucky.of(pillars, today)`. **내 일간 vs 오늘 일진 천간의 관계(십성)** 로 행운 오행 결정(비겁→식상, 식상→재성, 재성→관성, 관성→인성, 인성→비겁) → 오행별 풀에서 (일주 번호 + 일진 번호) 로 선택. 오행은 이틀 주기(천간 오행이 둘씩 같음), 아이템·장소는 매일. 사람마다 다르고 결정적
- 풀은 `resources/lucky/items.txt`, `places.txt` (`오행=항목|항목`). **임시값. 기획이 실제 물건·실제 장소로 교체**
- `Element` enum (오행, `luckyAgainst` 관계 대응), `ReadingScorer` 오행 표 통합. #12 의 "부족 오행 채우기" 는 일진이 의미 없어 폐기
- `ReadingGenerator` 스키마·프롬프트에서 `luckyItem`·`luckyPlace` 제거 → 출력 토큰 감소
- `result/`: `reading.lucky_*` 컬럼 삭제(V4), `ResultResponse.from()` 에서 KST 오늘 기준으로 계산. `ResultAnalysisPort.AnalysisResult` 에서 lucky 제거
- 테스트 52건

**건드린 파일/패키지**
- `saju/`: `DailyLucky`(신규), `Element`(신규), `Reading`, `ReadingGenerator`, `ReadingScorer`, `SajuCalculator`(toKorean 공개), 프롬프트, 리소스 2개
- `result/`(최선우 리뷰): `ResultAnalysisPort`, `SajuResultAnalysisAdapter`, `FakeResultAnalysisAdapter`, `ResultService`, `entity/Reading`, `dto/ResultResponse`, V4

**다음 사람이 알아야 할 것**
- `POST` 응답과 다음 날 `GET` 응답의 `luckyItem`·`luckyPlace` 가 다르다. 의도된 동작 (api-spec 명시)
- 일진은 lunar-java `getDayInGanZhi()` (GMT+8 기준이지만 날짜 단위라 KST 와 동일). 오늘 날짜는 `Asia/Seoul`
- 풀 항목 수가 달라도 됨. 오행별 최소 1개 없으면 기동 시 실패
- 오하아사식 "별자리 순위" 는 채택 안 함 (공식 API 없음, 별자리 기준이라 사주와 무관)
- 관계에 음양을 더하면 십성 10개(정재일·편재일…)로 "오늘 유형" 문구를 매일 다르게 만들 수 있음. 응답 필드 추가라 프론트 합의 후

**막힌 것 / 넘기는 것**
- ~~기획: `lucky/items.txt`·`places.txt` 실제 목록~~ 9/14 확정본 반영 (오행당 아이템 10개, 장소 3~4개)
- 최선우: `result/` 변경 리뷰

**문서 변경**
- `docs/api-spec.md` §2 (lucky 의미), `docs/architecture.md` §5·§6, `docs/handoff.md`

**프론트에 알려야 할 것**
- `luckyItem`·`luckyPlace` 가 매일 바뀜. 캐시하지 말 것

### 2026-09-13 (일) · 차은호 · saju/ 행운 오행 (#12) · Claude Code

**한 일**
- `Element` enum 추가: 천간·지지 → 오행, `Element.lacking(pillars)` = 팔자에서 가장 적은 오행(용신 근사). 결정적
- 프롬프트에 "행운 오행: 토 (색: 황색·갈색)" 힌트 추가. Gemini 는 그 오행의 색·소재·장소 안에서 아이템·장소를 고름
- `ReadingScorer` 의 오행 표를 `Element` 로 통합 (점수 변화 없음, 분포 재측정 동일)
- 스모크: 행운 오행 토 → 아이템 "약과", 장소 "후문 언덕". 이전엔 3건 연속 "정각원"·청색 편향

**건드린 파일/패키지**
- `saju/Element.java`(신규), `saju/ReadingScorer.java`, `saju/ReadingGenerator.java`, `resources/prompts/reading-system.txt`, 테스트

**다음 사람이 알아야 할 것**
- 행운 오행은 "부족 오행 보충" 관행. 정통 용신(신강·신약 판단)은 유파별로 달라 채택 안 함
- 응답에 행운 오행은 안 나감. 프론트가 "수 기운이 부족해서 파란색" 같은 문구를 원하면 `ResultResponse` 에 필드 추가만 하면 됨 (`Element.lacking(pillars)`)

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음

### 2026-09-13 (일) · 차은호 · result/ 연결 초안 (#10, 최선우 리뷰용) · Claude Code

**한 일**
- `POST /api/results` 를 가짜 분석기 대신 `saju/` 에 연결하는 초안. **`result/` 는 최선우 담당이라 Draft PR 로 올리고 최선우가 리뷰·머지**
- `CreateResultRequest`: `calendarType`(필수)·`isLeapMonth` 추가, `birthDate` 를 `String`(yyyy-MM-dd) 으로, `birthRegion` 제거
- `ResultService`: `BirthDate.parse(...).toSolar()` 로 양력 변환 후 계산·저장. 없는 날짜·윤달, 1950-01-01 ~ 오늘 밖 → 400 `INVALID_INPUT`
- `SajuResultAnalysisAdapter` 신규: `SajuCalculator` → `ReadingScorer` → `Grade.of` → `ReadingGenerator`. **기본 활성**. 가짜 분석기는 `app.result.fake-analysis-enabled=true` 일 때만 (기본값 뒤집음)
- `ResultAnalysisPort.analyze(LocalDate solar, LocalTime)` — 지역 파라미터 제거
- `ResultResponse` 에 `zodiac`
- 로컬 E2E (Postgres 15 + 실제 Gemini): 양력·음력 윤달·시간 모름 201, 없는 윤달·형식 오류·1949년 400. Gemini 3.1~3.4초/건

**건드린 파일/패키지**
- `result/`: `CreateResultRequest`, `ResultService`, `ResultAnalysisPort`, `FakeResultAnalysisAdapter`, `SajuResultAnalysisAdapter`(신규), `dto/ResultResponse`, 테스트 2개

**다음 사람이 알아야 할 것**
- **해결됨**: `result.birth_time` 이 입력 14:30 대신 DB에 05:30으로 저장되던 문제는 `Result.birthTime`을 JDBC 네이티브 `LOCAL_TIME`으로 매핑해 전역 UTC 보정 대상에서 제외함
- `reading` 에 **점수(0~100)를 저장**한다 (V3, 9/13 결정). 등급은 응답 시 `Grade.of(score).label()`. 등급 컷을 바꿔도 마이그레이션 없이 코드만 고치면 됨
- LLM 실패 시 팔자를 미리 저장해 재시도 가능하게(FR-RD-07) 하는 건 안 넣음. 계산이 수 ms 라 재요청 시 재계산이 더 단순
- 옛 9/12 기록의 "`fake-analysis-enabled=false` 로 끈다" 는 반대로 바뀜: 기본이 실제, `true` 면 가짜

**막힌 것 / 넘기는 것**
- 최선우: PR 리뷰·머지

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (api-spec 은 이미 반영됨)

### 2026-09-13 (일) · 차은호 · saju/ 등급·해석 (#6) · Claude Code

**한 일**
- `ReadingCategory` 를 api-spec 기준 3종(MARRIAGE, CHILDREN, LOVE)으로 교체. 옛 5종 스텁 제거
- `Grade` 6단계 `SS S A+ A B+ B` (기획 확정). 점수→등급 경계는 `Grade.of()` 한 곳
- `ReadingScorer`: 팔자 → **0~100 점수**. 일간 기준 십성 분포·일지(배우자궁)·시지(자녀궁)·합충·도화살 가중치. 순수 함수. 등급 변환은 API 계층에서 `Grade.of(score)` (궁합의 점수→Tier 와 같은 구조)
- `ReadingGenerator`: Gemini `gemini-2.5-flash` 1회 호출, JSON 스키마 강제, thinking 0, 재시도 1회, 실패 시 `LLM_UNAVAILABLE`. 로그는 ms·토큰 수만
- 프롬프트 `resources/prompts/reading-system.txt` (보살 톤, 행운 장소는 동국대 캠퍼스 안)
- 테스트 8건: 등급 결정성·경계, 프롬프트 개인정보 미포함, 파싱 실패

**건드린 파일/패키지**
- `saju/` 만: `ReadingCategory`, `Grade`, `Reading`, `ReadingScorer`, `ReadingGenerator`, `resources/prompts/`

**다음 사람이 알아야 할 것**
- 등급 분포(1950~2010 고유 팔자 8,225개): 결혼 SS 13%·B 4%, 자녀 SS 10%·B 16%, 연애 SS 4%·B 8%. 기획이 더 후하게/짜게 원하면 `ReadingScorer` 상수만 조정
- Gemini 모델 `gemini-3.6-flash` (`gemini.model`, 기본값). **`gemini-2.5-flash` 는 신규 키에 404** ("no longer available to new users"). 3.x 는 `thinkingBudget` 400 → `thinkingLevel: MINIMAL` 사용
- 실제 호출 스모크 완료: 약 3초, 약 770 토큰/건, 응답 스키마 7필드 정상. `GeminiSmokeTest` 는 `GOOGLE_API_KEY` 있을 때만 실행(CI 제외)
- 최선우 연결 지점: `SajuCalculator.calculate()` → `ReadingScorer.score()` (점수) → `Grade.of(score)` (등급) → `ReadingGenerator.generate(pillars, grades)`. `Reading.contents()` 는 `EnumMap<ReadingCategory,String>`, 응답 `grade` 는 `Grade.label()`. 점수를 저장할지 등급만 저장할지는 `reading` 테이블 설계(최선우) 몫
- 런타임 LLM 유지 결정(9/13). 대안으로 검토한 사전 생성(카테고리 3 × 등급 6 × 일주 60 문장을 미리 만들어 리소스로, 런타임 호출 0회)은 축제 순간 트래픽·429 위험을 없애는 방법. 무료 한도 실측 후 부족하면 이 안으로 전환 가능

**막힌 것 / 넘기는 것**
- Day 6 RPM·RPD 실측 (무료 티어 한도가 축제 트래픽에 부족하면 결제 연결 — 곽도윤)

**문서 변경**
- `docs/handoff.md`

**프론트에 알려야 할 것**
- 없음 (등급 6단계는 PR #5 에서 api-spec 반영됨)
### 2026-09-13 (일) · 차은호 · 문서 (#7, 기획 명세 반영) · Claude Code

**한 일**
- 프론트 기능명세서(Figma v0.2, 9/13)와 기획 결정을 백엔드 문서에 반영
- 궁합 Tier 구간 변경 기록: 귀인 90~100 / 찰떡 75~89 / 벗 61~74 / 스침 0~60. **코드(`CompatibilityCalculator`·`CompatibilityService`)는 최선우가 반영**

**건드린 파일/패키지**
- `docs/api-spec.md` §4, `docs/architecture.md` §7, `docs/backend-requirements.md` FR-CP-03·TR-06·인수 조건, `docs/convention.md`, `docs/handoff.md`

**다음 사람이 알아야 할 것 — 프론트 명세와 어긋나는 것 (팀 결정 필요)**
- **API 계약 불일치.** 프론트 명세는 `POST /readings`, `GET /shares/{shareId}`, `POST /shares/{shareId}/compatibility`, `GET /me/friends`, `POST /me/threads` 와 응답 타입 `Reading{id, shareId, zodiac, destiny, sections(3), cardGrades, lucky}` 를 쓰고, 원본을 "백엔드 저장소 `docs/api/openapi.yaml`" 이라고 적음. **우리 레포에 그 파일 없음.** 우리 계약은 `docs/api-spec.md` (`/api/results`, `resultId`, `destiny`, `fortunes`). 프론트 Open Question Q3 담당은 `@hairyung2002`. 9/17 전에 한쪽으로 맞춰야 연동 가능
- **요청 형식.** 프론트는 `birthDate` "숫자 8자리", 우리는 `yyyy-MM-dd`. 시진 선택 UI 인데 전송 규칙(가운데 시각, 자시 두 칸)이 프론트 문서에 없음 → api-spec §2 를 프론트에 공지해야 함
- ~~**사전신청 수집 항목.** 프론트 FR-10 은 학교 이메일·이름·연락처(인스타/전화)·사진·학과·나이·MBTI·자기소개 를 Must 로 수집. 우리 규칙은 "이름·전화번호는 받지 않는다, 컬럼도 없다". `signup/` 스키마·개인정보 정책 결정 필요 (곽도윤)~~ → **2026-09-15 해결.** 이름·연락처·학과·MBTI·자기소개 수집으로 정책 변경(FR-SU-11 개정, V7). 사진은 2차 릴리즈로 보류(FR-SU-16). 나이는 이번 API에 포함 안 함 — 프론트 확인 필요
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
- ~~기획 결정 대기 1건: 자시 두 칸 분리~~ 9/14 승인. 프론트: `자시 00:00~01:30` → `00:45`, `자시 23:30~24:00` → `23:45`
- 최선우: `CreateResultRequest` 에 `CalendarType calendarType`(saju 패키지 enum)·`Boolean isLeapMonth` 추가, `birthDate` 를 `String` 으로, `birthRegion` 삭제. `BirthDate.parse(...).toSolar()` 결과(양력)를 `analyze()` 와 `Result` 저장에 사용
- 최선우: 응답에 `zodiac` 추가 — `SajuPillars.zodiac()` (또는 저장된 `year_pillar` 로 `Zodiac.fromYearPillar`). 컬럼 추가 불필요
- `saju/` 출력 형태 미확정: `ReadingCategory` 5종(점수) vs `result/ResultAnalysisPort` (운명 + 결혼·자녀·연애 등급 + 행운 아이템·장소). 등급 문자열 집합·산출 기준도 미정. 이게 정해져야 `ReadingScorer`·`ReadingGenerator` 착수 가능
- TR-01 대체: 포스텔러 만세력 2.2 와 7건 대조(입춘 전후·자시·시진 경계·설날) 전부 일치, `SajuCalculatorTest.matchesPosteller` 에 고정. 실제 인물 데이터가 생기면 추가

**문서 변경**
- `docs/architecture.md`: 스택 표에 lunar-java 추가, §9 미결정에서 만세력 항목 제거, §6 에 계산 방식 반영
- `docs/api-spec.md` §2: 음력 필드 2개 추가, 시진 입력 규칙 추가 (필드 추가라 사전 합의 불필요, 프론트 공지 필요)

**프론트에 알려야 할 것** (기획 확인 후 공지, 2026-09-13 기획에 전달)
- 시간은 시진(2시간) 선택 UI 그대로. 프론트가 선택한 칸의 **가운데 시각**을 `birthTime: HH:mm` 으로 보낸다 (묘시 05:30~07:30 → `06:30`). 같은 시진이면 팔자가 같아서 문제 없음
- **자시 두 칸 분리 확정(9/14 기획)**: `자시 00:00~01:30` → `00:45`, `자시 23:30~24:00` → `23:45`. 자정을 걸쳐서 날짜+자시만으로는 새벽/밤 구분이 안 되고, 둘은 일주가 하루 다름
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
### 2026-09-13 (일) · 최선우 · result/·compatibility/ · Codex

**한 일**
- `GET /api/results/{resultId}` 구현 및 Swagger 명세 추가
- Result·Reading DB 조회와 궁합 최신순 조회 연결
- `FakeResultAnalysisAdapter`를 제거하고 `SajuCalculator` → `ReadingScorer` → Gemini 해석 경로를 유일한 `ResultAnalysisPort` 구현으로 고정
- 잘못된 UUID 400, 없는 Result 404 처리
- 조회 시 A 분석 모듈 미호출 단위 테스트 및 실제 PostgreSQL 재조회 검증

**건드린 파일/패키지**
- `result/`의 Controller, Service, 응답 DTO와 테스트
- `compatibility/CompatibilityRepository`

**다음 사람이 알아야 할 것**
- POST와 GET이 `ResultResponse`를 공유하며 POST의 `compatibilities`는 빈 배열
- 궁합 조회는 origin/guest 양쪽을 한 쿼리로 조회하고 `createdAt` 내림차순 정렬

**막힌 것 / 넘기는 것**
- 없음

**문서 변경**
- `docs/api-spec.md`에 POST 빈 궁합 배열 반영

**프론트에 알려야 할 것**
- POST 응답에도 `compatibilities: []`가 포함됨

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

### 2026-09-13 (일) · 곽도윤 · 인프라 · Claude Code

**한 일**
- 배포 파이프라인 초안 구성: `Dockerfile`(멀티스테이지 빌드), `docker-compose.prod.yml`(postgres+app+nginx), `nginx/default.conf`
- `application-prod.yml` 신설 — base `application.yml`엔 datasource가 아예 없어서(로컬 프로필에만 존재) prod로 그냥 못 띄우는 상태였음. 전부 환경변수 주입으로 처리
- `.github/workflows/deploy.yml` 추가 — `main` push 시 GHCR로 이미지 빌드/푸시 → **`docker-compose.prod.yml`/`nginx/` 를 scp로 EC2에 동기화** → SSH로 `docker compose pull && up -d` + nginx restart 자동 실행
  - (처음엔 이미지 배포만 자동화했다가, compose/nginx 파일이 바뀌면 여전히 수동 scp가 필요한 구멍이 있어서 sync 단계 추가함)
- `.env.prod.example` 추가 (실값은 EC2에서 `.env`로 채우고 git에 안 올림 — 이건 비밀값이라 앞으로도 계속 수동)

**건드린 파일/패키지**
- `Dockerfile`, `.dockerignore`, `docker-compose.prod.yml`, `nginx/default.conf`, `.env.prod.example`
- `src/main/resources/application-prod.yml` (신규)
- `.github/workflows/deploy.yml` (신규)
- `.gitignore` (`.env`, `.env.prod` 추가)

**다음 사람이 알아야 할 것**
- EC2에서 실행할 땐 `SPRING_PROFILES_ACTIVE=prod` 필수 (compose 파일에 이미 박아둠)
- GHCR 이미지가 private면 EC2에서도 `docker login ghcr.io` 필요 — 워크플로에 포함돼 있음
- nginx는 HTTP(80)만 열어둔 상태. 도메인 정해지면 `nginx/default.conf`의 `server_name` 채우고 certbot으로 443 추가할 것

**막힌 것 / 넘기는 것**
- **GitHub Secrets 미등록** → `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_APP_DIR` 를 리포지토리 Settings에 등록해야 워크플로가 실제로 돈다
- **EC2 인스턴스 자체가 아직 없음** → 인스턴스 생성, 보안그룹(80/443/22 오픈), Docker 설치, 최초 1회 `docker-compose.prod.yml`+`nginx/`+`.env` 서버에 배치 필요
- 프론트 배포 도메인 미확정 → CORS_ALLOWED_ORIGINS/nginx server_name 임시값으로 둠

**문서 변경**
- `architecture.md`의 기존 인프라 결정(Docker·EC2·nginx·GitHub Actions)은 그대로 유지, 구체 구현만 추가

**프론트에 알려야 할 것**
- 없음 (API 변경 아님)

---

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

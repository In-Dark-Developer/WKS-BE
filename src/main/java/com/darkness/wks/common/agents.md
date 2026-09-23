# common/ — 3인 합의 영역

공통 응답·예외·설정. **단독 수정 금지.** 변경 전 팀 채널에 알린다.
3명이 모두 건드리는 곳이라 충돌이 가장 잦다. 특히 `ErrorCode`.

## ErrorCode

`docs/api-spec.md` 의 에러 코드 표와 **1:1로 대응**한다. 한쪽만 고치지 마라.
새 코드를 추가하기 전에 `docs/handoff.md` 의 "ErrorCode 추가 현황" 표에 적는다.

## 응답 포맷

```json
{ "success": true,  "data": { ... } }
{ "success": false, "error": { "code": "...", "message": "..." } }
```

`error.message` 는 사용자에게 그대로 보여줄 한국어다.
**스택트레이스·SQL 오류·내부 경로를 절대 노출하지 마라.**

## GlobalExceptionHandler

`BusinessException`, `MethodArgumentNotValidException`, `NoHandlerFoundException`, `Exception` 처리.
예상치 못한 예외는 로그에만 상세를 남기고 응답은 `INTERNAL_ERROR` 로 통일한다.
Controller에 `try-catch` 를 뿌리지 마라.

## config

- `WebConfig` — CORS. `allowedOrigins` 에 와일드카드(`*`) 절대 금지
- `OpenApiConfig` — Swagger. 운영에서 끌 수 있게 설정값으로 노출
- `TraceIdFilter` — 요청마다 traceId 발급 → MDC → 로그·에러 응답에 포함
- Spring Security는 쓰지 않는다. 로그인은 JWT 인터셉터(`auth/`)로 처리한다 (`docs/architecture.md` §4)
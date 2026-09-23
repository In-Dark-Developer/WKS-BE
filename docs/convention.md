# convention.md

3명이 **각자 다른 AI 도구로** 코드를 만든다.
AI는 자기 문맥 안에서만 일관되게 짜기 때문에, 합칠 때 어긋나는 걸 스스로 알아채지 못한다.
그래서 규칙을 문서로 고정한다.

---

## 기본

- 들여쓰기 4칸, 파일 끝 개행 1개, LF
- 주석은 **왜**를 쓴다. 무엇을 하는지는 코드가 말한다
- `// TODO(이름): 내용` — 이름 없는 TODO 금지
- 한글 주석 허용

`.editorconfig` 를 반드시 둔다. 나중에 포매터를 도입하면 전체 파일이 diff에 걸려 리뷰가 불가능해진다.

---

## 네이밍

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 | PascalCase | `ResultService` |
| 메서드·변수 | camelCase | `createResult` |
| 상수 | UPPER_SNAKE | `MAX_NICKNAME_LENGTH` |
| 요청 DTO | `~Request` | `CreateResultRequest` |
| 응답 DTO | `~Response` | `ResultResponse` |
| 테이블 | snake_case **단수** | `result`, `reading` |
| 테스트 | `~Test` | `ReadingScorerTest` |

### 도메인 용어 (통일)

| 용어 | 코드 표기 |
|---|---|
| 사주 결과 1건 | `Result`, `resultId` |
| 사주 팔자 | `Pillar` |
| 카테고리별 해석 | `Reading` |
| 궁합 점수 | `Compatibility` |
| 궁합 4등급 (귀인·찰떡·벗·스침) | `CompatibilityTier` |
| 사전등록 | `Signup` |

**금지어**: `saju`(클래스명으로), `user`(유저 개념이 없다), `match`(2차 전용), `fortune`

---

## 계층 규칙

```
Controller  →  HTTP 만. 비즈니스 로직 0줄
Service     →  로직·트랜잭션. 여기가 본체
Repository  →  DB 접근만
```

- **Controller 는 Entity 를 반환하지 않는다.** 반드시 DTO
- **Entity 는 Controller 계층에 올라오지 않는다**
- `@Transactional` 은 Service 에만. 조회는 `readOnly = true`

---

## DTO

`record` 를 쓴다. DTO에 Lombok `@Data` 를 붙이지 않는다.

```java
public record CreateResultRequest(
    @NotBlank @Size(max = 20) String nickname,
    @NotNull @Past LocalDate birthDate,
    LocalTime birthTime,          // nullable — 시간 모름
    @Size(max = 50) String birthRegion,
    @NotNull Gender gender
) {}
```

응답 DTO는 정적 팩토리로 변환한다. 변환 로직을 Service에 흩뿌리지 않는다.

```java
public record ResultResponse(...) {
    public static ResultResponse from(Result result, List<Reading> readings) { ... }
}
```

---

## Entity

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Result {
    @Id
    private UUID id;
}
```

- **`@Setter` 금지.** 상태 변경은 의미 있는 메서드로 (`signup.verify()`)
- `@Data`, `@AllArgsConstructor` 금지
- 연관관계는 `LAZY` 고정. **`EAGER` 금지**
- 억지 양방향 매핑 금지
- enum은 `@Enumerated(EnumType.STRING)`

---

## 예외 처리

```java
throw new BusinessException(ErrorCode.RESULT_NOT_FOUND);
```

- Controller에 `try-catch` 를 뿌리지 않는다. `@RestControllerAdvice` 하나로 처리
- `ErrorCode` 는 `docs/api-spec.md` 의 표와 **1:1 대응**. 한쪽만 고치지 않는다
- 예상치 못한 예외는 로그에만 상세를 남기고 응답은 `INTERNAL_ERROR`

---

## Flyway

- 파일명: `V{번호}__{설명}.sql` — `V2__add_signup_memo.sql`
- **머지된 마이그레이션 파일은 절대 수정하지 않는다.** 새 파일을 추가한다
- 번호 충돌 방지: 파일 만들기 **전에** `docs/handoff.md` 예약 표에 적는다
- `V1__init.sql` 은 `docs/architecture.md` 의 스키마를 그대로 옮긴다

---

## 로깅

```java
@Slf4j
log.info("result created. id={}", result.getId());
```

- `System.out.println` 금지
- **생년월일시·이메일을 평문으로 찍지 않는다.** `resultId`, `signupId` 만
- LLM 요청·응답 전문을 로그에 남기지 않는다. 토큰 수·소요 시간만
- 모든 로그에 `traceId` 가 찍힌다 (`TraceIdFilter` + MDC)

---

## 설정

- 시크릿은 환경변수. `application.yml` 하드코딩 금지
- 로컬 설정은 `application-local.yml` — **`.gitignore` 대상**.
  저장소에는 `application-local.yml.example` 만 둔다
- `@Value` 를 흩뿌리지 말고 `@ConfigurationProperties` 로 묶는다

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate      # Flyway 쓰므로 고정
    open-in-view: false       # 기본 true — 커넥션 풀 조기 고갈 방지
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
  datasource:
    hikari:
      connection-timeout: 3000
      maximum-pool-size: 10
server:
  error:
    whitelabel:
      enabled: false          # 404에 HTML이 나오면 프론트 JSON 파싱 실패
management:
  endpoints:
    web:
      exposure:
        include: health       # 전부 열지 않는다
```

**타임아웃 없는 외부 호출이 하나라도 있으면 그게 서비스를 죽인다.**
DB·SMTP·LLM 전부 명시적으로 설정한다.

---

## 테스트

1주 일정이라 전부는 못 짠다. **아래 5개는 필수**다.

| 대상 | 담당 | 이유 |
|---|---|---|
| 사주 팔자 계산 — 실제 인물 5명 대조 | 차은호 | 틀리면 서비스가 무의미 |
| 시간·지역 **null** 케이스 | 차은호 | NULL 처리에서 가장 자주 터진다 |
| 궁합 대칭성 `score(A,B)==score(B,A)` | 최선우 | 깨지면 유저가 즉시 알아챈다 |
| 해석 캐싱 — 재조회 시 LLM 호출 0회 | 최선우 | 비용·지연 직결 |
| Tier 경계값 60/61/74/75/89/90 | 최선우 | off-by-one 빈발 |

### 환경 규칙

- **LLM은 반드시 목킹.** 테스트에서 실제 API를 때리지 않는다
- DB 테스트는 **Testcontainers(PostgreSQL)**. H2는 문법이 달라 로컬 통과/배포 실패가 난다
- 통합 테스트는 공통 베이스 클래스를 상속한다. 각자 `@SpringBootTest` 설정을 짜면 CI가 느려진다
- `saju/` 는 스프링 컨텍스트 없이 순수 단위 테스트

---

## 커밋 메시지

```
feat: 사주 결과 생성 API 구현
fix: 시간 모름 입력 시 시주 계산 오류 수정
docs: api-spec 궁합 응답 형식 수정
chore: Flyway PostgreSQL 모듈 추가
refactor: ReadingScorer 카테고리별 분리
test: 궁합 점수 대칭성 테스트 추가
```

제목 한국어, 50자 이내, 마침표 없음.
**AI가 생성한 커밋 메시지를 그대로 쓰지 마라.** 본인 말로 쓴다.

---

## AI 도구로 작업할 때

세 명이 서로 다른 도구를 쓴다. 도구는 달라도 아래는 공통이다.

- **작업 단위를 작게.** "결과 API 만들어줘" ✗ / "ResultResponse 변환 메서드만" ○
- **기존 파일을 먼저 읽히고** 스타일을 맞추게 한다
- **AI가 만든 코드도 PR 리뷰를 거친다.** 예외 없음
- AI가 새 라이브러리를 추천하면 **일단 거절**하고 팀에 물어본다
- AI가 `docs/` 와 다르게 짰으면 **문서가 맞다.** 코드를 고친다

### AI가 자주 어기는 것 (리뷰 시 확인)

| 항목 | 왜 생기나 |
|---|---|
| Spring Security 추가 | "Spring Boot 세팅"의 기본값처럼 학습돼 있다 |
| Entity에 `@Setter`, `@Data` | 예제 코드에 흔하다 |
| `ddl-auto: update` | 튜토리얼 기본값 |
| springdoc **2.x** | Boot 3 기준 학습. Boot 4에서는 기동 실패 |
| Jackson 2 API | Boot 4는 Jackson 3 |
| `TIMESTAMP` (TZ 없음) | 무심코 |
| `name`, `phone` 컬럼 추가 | "회원 정보니까 있어야 할 것 같아서" |
| 패키지명 임의 변경 | 자기 관례로 바꾼다 |
| 만세력 임의 구현 | "할 수 있을 것 같아서" |
| 프롬프트에 생년월일·닉네임 삽입 | "정보가 많을수록 좋은 답이 나온다고 학습" |
| 카테고리별 LLM 개별 호출 | 자연스러운 구조로 보인다. 무료 티어 한도를 5배로 태운다 |

**이 표는 실제로 겪은 것들이다.** PR 리뷰에서 이것부터 본다.
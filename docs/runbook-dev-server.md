# runbook-dev-server.md

EC2에서 **직접 실행**하는 문서다. 여기 적힌 명령은 이 세션이 실행하지 않는다 — 순서대로 사람이 실행한다.
⚠️ 표시가 붙은 단계는 운영(`api.threadoffate.site`)에 영향을 줄 수 있다. 트래픽이 적은 시간에 실행한다.

전제: 같은 EC2, 같은 Elastic IP에서 nginx·postgres 컨테이너 하나씩을 운영·개발이 공유한다 (`docs/architecture.md`, `docker-compose.prod.yml` 참고).

> **실행 이력: 2026-09-26 곽도윤이 1~9단계 완료, 운영·개발 모두 `/api/health` 200.** 아래 본문은 그때 실제로 막힌 곳을
> 반영해 고친 판이다. 다시 처음부터 할 일은 없고, 서버를 새로 만들 때나 장애 복구 때 참고한다.
> 이후 반영할 것: `nginx/api-dev.conf` 가 바뀌면 11단계를 따른다.

**명령어 붙여넣기 주의**: `\` 로 줄을 이은 여러 줄 명령을 SSH 터미널에 붙여넣으면 줄바꿈이 깨져 옵션이 합쳐질 수
있다(certbot 이 `unrecognized arguments: --webroot` 로 실패했다). 한 줄로 합쳐서 붙여넣는다.

---

## 0. 사전 조건

- `api-dev.threadoffate.site` A 레코드가 Route53에 추가돼 있다 (본인 처리, 이 문서 범위 밖)
- 아래 파일들이 이미 `dev`(→`main`, 운영 쪽 파일은 `main`까지) 브랜치에 머지돼 EC2로 받을 준비가 됐다:
  `docker-compose.dev.yml`, `.env.dev.example`, `nginx/api-dev.bootstrap.conf`, `nginx/api-dev.conf`, `db/init/init-wks-dev.sql`
- **운영 compose 변경(wks-edge 네트워크 추가)은 2026-09-23 곽도윤이 diff를 확인·승인했다** (`docker-compose.prod.yml`에 이미 반영됨). EC2에 실제로 반영하는 지점이 이 문서의 2-2단계다
- **EC2 에는 저장소 전체가 없다.** `deploy.yml` 은 `docker-compose.prod.yml`·`nginx/` 만, `deploy-dev.yml` 은
  `docker-compose.dev.yml` 만 보낸다. `db/init/init-wks-dev.sql`·`.env.dev.example` 은 직접 옮기거나 EC2 에서 만든다

파일을 로컬(Windows)에서 EC2 로 보낼 때는 `.pem` 키로 `scp` 를 쓴다 (`.ppk` 는 PuTTY 전용이라 OpenSSH `scp` 가 못 읽는다 — 그땐 `pscp`):

```powershell
scp -i "<키 경로>\WKS.pem" "<저장소 경로>\<파일>" ubuntu@<Elastic IP>:/opt/wks-dev/
```

`UNPROTECTED PRIVATE KEY FILE` 로 거부되면 키 파일 권한부터 좁힌다:

```powershell
icacls "<키 경로>\WKS.pem" /inheritance:r
icacls "<키 경로>\WKS.pem" /grant:r "$($env:USERNAME):(R)"
```

---

## 1. 사전 확인

```bash
free -h                                  # 메모리 여유 확인 (t3.small 업그레이드 전이면 특히)
docker ps                                # 현재 wks-app, wks-nginx, wks-certbot, wks-postgres 4개 떠있는지
df -h                                    # 디스크 여유 (#14 사고: 루트 파티션이 가득 차면 배포가 실패했었다)
dig api-dev.threadoffate.site +short     # Elastic IP로 resolve 되는지 (0단계 확인)
```

`dig` 결과가 비어있으면 Route53 전파를 기다린다 — 이후 단계(특히 인증서 발급)는 DNS가 안 맞으면 실패한다.

---

## 2. wks-edge 네트워크 생성

```bash
docker network create wks-edge
docker network ls | grep wks-edge        # 확인
```

### 2-1. nginx 마운트용 실 파일을 미리 만들어둔다 (필수 선행 작업)

**이 단계를 건너뛰고 2-2를 실행하면 운영 nginx가 기동에 실패한다.** 2-2에서 추가하는 볼륨 마운트는
호스트의 `nginx/api-dev.conf.active` 파일 하나를 컨테이너 안 `api-dev.conf`로 연결한다. 이 파일이 마운트
시점에 없으면 Docker가 **빈 디렉터리를 대신 만들어버리고**, nginx는 그 디렉터리를 설정 파일로 읽으려다
기동 실패한다 — 같은 nginx 컨테이너를 쓰는 운영까지 같이 죽는다. 그래서 5단계(80번 블록 적용)보다
먼저, 네트워크 변경 전에 파일 실체부터 만들어둔다.

```bash
cd /opt/wks
cp nginx/api-dev.bootstrap.conf nginx/api-dev.conf.active
ls -la nginx/api-dev.conf.active     # 파일(디렉터리 아님)인지 확인
```

### 2-2. ⚠️ 운영 compose에 wks-edge 네트워크 추가 (승인 완료, EC2 반영은 아래에서)

`docker-compose.prod.yml`의 wks-edge 네트워크 추가는 이미 승인·반영됐다 (2026-09-23). 이 커밋이 `main`까지
릴리즈돼 `deploy.yml`(트리거 `main`)이 EC2의 `/opt/wks/docker-compose.prod.yml`을 갱신한 뒤, 또는 최초 1회는
수동으로 파일을 갱신한 뒤 아래를 실행한다.

```bash
cd /opt/wks
ls -la nginx/api-dev.conf.active                               # 2-1을 먼저 했는지 재확인 (파일이어야 함)
docker compose -f docker-compose.prod.yml config                # 문법 확인
docker compose -f docker-compose.prod.yml up -d                 # 변경된 서비스만 재생성됨
docker ps                                                        # wks-app·wks-nginx·wks-postgres 다시 healthy 확인
docker compose -f docker-compose.prod.yml logs nginx --tail 30   # nginx가 api-dev.conf(80번만)까지 정상 로드했는지 확인
curl -fsS https://api.threadoffate.site/api/health               # 운영 정상 확인
```

**예상 다운타임: 약 30~60초.** postgres·app·nginx 세 컨테이너 모두 네트워크 설정이 바뀌어 재생성된다.
postgres는 수 초 내 재기동하지만 그 사이 앱의 DB 커넥션이 끊겼다 재연결된다. 가장 오래 걸리는 건 app(Spring Boot JVM 기동, 보통 15~30초) 재기동이다. 데이터는 named volume에 있으므로 유실되지 않는다.

**실패 시 롤백**: 변경 전 `docker-compose.prod.yml`로 되돌리고 다시 `up -d`.

---

## 3. 개발 DB·계정 생성

`/tmp/init-wks-dev.filled.sql` 은 저절로 생기지 않는다. 내용이 짧으니 **EC2 에서 바로 만든다** (저장소의
`db/init/init-wks-dev.sql` 에서 주석을 뺀 것과 같다). 비밀번호는 4단계 `.env` 의 `DB_PASSWORD` 와 같은 값을 쓴다.
저장소 원본은 플레이스홀더 그대로 둔다 — 채운 사본을 커밋하지 않는다.

```bash
cat > /tmp/init-wks-dev.filled.sql <<'EOF'
CREATE ROLE wks_dev WITH LOGIN PASSWORD '<비밀번호>';
CREATE DATABASE wks_dev OWNER wks_dev;
REVOKE CONNECT ON DATABASE wks FROM PUBLIC;
GRANT CONNECT ON DATABASE wks TO wks;
REVOKE CONNECT ON DATABASE wks_dev FROM PUBLIC;
GRANT CONNECT ON DATABASE wks_dev TO wks_dev;
EOF

cd /opt/wks
docker compose -f docker-compose.prod.yml exec -T postgres psql -U wks -d wks -v ON_ERROR_STOP=1 -f - < /tmp/init-wks-dev.filled.sql
rm /tmp/init-wks-dev.filled.sql   # 비밀번호가 든 임시 파일은 바로 지운다
```

비밀번호를 나중에 바꿀 때 (`wks` 는 슈퍼유저라 `wks_dev` DB 에 붙을 수 있다). 바꾼 뒤 `.env` 도 같이 고친다:

```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U wks -d wks_dev -c "ALTER ROLE wks_dev WITH PASSWORD '<새 비밀번호>';"
```

확인 (**핵심 방향**: 아래 명령이 permission denied로 실패해야 정상):

```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U wks_dev -d wks -c '\conninfo'
```

반대 방향(`wks`로 `wks_dev` 접속)은 **막히지 않는다** — `wks`는 이 postgres 컨테이너의 슈퍼유저라
REVOKE를 우회한다 (로컬 검증 완료, `db/init/init-wks-dev.sql` 주석 참고). 운영 계정이 개발 DB를
읽을 수 있다는 뜻이라 이상적이진 않지만, 막아야 할 더 중요한 방향(개발 쪽에서 운영 개인정보 접근)은
지켜진다.

---

## 4. /opt/wks-dev 디렉토리 생성, 파일 배치, .env 작성

```bash
sudo mkdir -p /opt/wks-dev
sudo chown $USER:$USER /opt/wks-dev
cd /opt/wks-dev
```

`docker-compose.dev.yml`은 `dev` push마다 `deploy-dev.yml`이 자동으로 여기에 scp한다 — 최초 1회는 0단계의 `scp` 로
`docker-compose.dev.yml`·`.env.dev.example` 두 개를 옮긴다. 옮긴 뒤 `cat .env.dev.example` 로 내용이 env 템플릿인지
먼저 본다 (로컬 파일이 compose 내용으로 덮여 있던 채로 보내져 두 파일이 똑같아진 적이 있다).

**실제 값은 EC2 의 `.env` 에만 쓴다. 로컬 저장소의 `.env.dev.example` 을 채우지 않는다** — 커밋 대상 템플릿이다
(실제 Gemini 키를 여기 채웠다가 되돌린 적이 있다).

```bash
cp .env.dev.example .env
vi .env                           # 실제 값 채우기
chmod 600 .env
```

| 키 | 주의 |
|---|---|
| `DB_PASSWORD` | 3단계 `wks_dev` 비밀번호와 같은 값 |
| `JWT_SECRET` | **32바이트 이상**(`openssl rand -base64 32`). 짧으면 `JwtProvider` 가 기동을 막는다 — 문자열을 UTF-8 바이트 그대로 키로 쓰고 base64 디코딩은 안 한다. 운영과 다른 값 |
| `GOOGLE_API_KEY` | 운영과 다른 키. 같으면 dev 호출이 운영 하루 한도(`gemini.max-per-day`)를 깎는다 |
| `KAKAO_*` | 개발용 카카오 앱 키. 비워도 앱은 뜬다(로그인만 `KAKAO_UNAVAILABLE`) |
| `MAIL_*`·`AWS_*` | 비워도 앱은 뜬다(해당 기능만 동작 안 함) |

값은 대화·채팅에 `cat` 으로 붙여넣지 않는다. 빈 키만 확인한다 (아무것도 안 나오면 전부 채워진 것):

```bash
grep -E '^[A-Za-z_]+=$' .env
```

---

## 5. ⚠️ nginx에 80번 블록만 먼저 적용

인증서가 아직 없는 상태에서 443 블록을 넣으면 **nginx 컨테이너 전체가 기동 실패**하고 운영 api까지 같이 죽는다.
2-1에서 이미 `nginx/api-dev.conf.active`를 bootstrap(80번 전용) 내용으로 만들어뒀다 — 여기서는 그게
실제로 반영됐는지 확인하고 reload만 한다.

```bash
cd /opt/wks
diff nginx/api-dev.bootstrap.conf nginx/api-dev.conf.active   # 차이 없어야 함 (2-1에서 만든 그대로)
docker compose -f docker-compose.prod.yml exec nginx nginx -t     # ⚠️ 반드시 먼저 문법 검사
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
curl -fsS http://api-dev.threadoffate.site/.well-known/acme-challenge/ping || true   # 404/실패는 정상 (경로가 없어서) — nginx가 응답하는지만 확인
curl -fsS https://api.threadoffate.site/api/health    # 운영 영향 없는지 확인
```

**`nginx -t`가 실패하면 절대 reload하지 않는다.** 실패 메시지를 고치고 다시 `-t`부터.

---

## 6. 인증서 발급

먼저 dry-run:

```bash
cd /opt/wks
docker compose -f docker-compose.prod.yml run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot -d api-dev.threadoffate.site \
  --email <이메일> --agree-tos --no-eff-email --dry-run
```

dry-run 성공 후 실제 발급 (`--dry-run` 만 제거):

```bash
docker compose -f docker-compose.prod.yml run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot -d api-dev.threadoffate.site \
  --email <이메일> --agree-tos --no-eff-email
```

인증서는 호스트 경로가 아니라 `certbot-etc` named volume 에 들어간다. 호스트에서 `sudo ls /etc/letsencrypt/...` 하면
**성공했어도 항상 없다고 나온다.** 볼륨을 마운트한 nginx 컨테이너 안에서 확인한다:

```bash
docker compose -f docker-compose.prod.yml exec nginx ls -la /etc/letsencrypt/live/api-dev.threadoffate.site/   # fullchain.pem, privkey.pem
sudo ls /etc/letsencrypt/live/api-dev.threadoffate.site/   # fullchain.pem, privkey.pem 확인
```

**실패 시**: 대부분 DNS 전파 미완료(`dig` 재확인) 또는 5단계 80번 블록이 제대로 reload 안 된 경우다. `docker compose -f docker-compose.prod.yml logs nginx`로 확인.

---

## 7. ⚠️ nginx에 443 블록 적용

```bash
cd /opt/wks
cp nginx/api-dev.conf nginx/api-dev.conf.active    # bootstrap → 최종본으로 교체
docker compose -f docker-compose.prod.yml exec nginx nginx -t     # ⚠️ 반드시 먼저 문법 검사
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
curl -fsS https://api.threadoffate.site/api/health    # 운영 영향 없는지 재확인
```

`nginx -t` 통과 전에는 절대 reload하지 않는다 — 6단계에서 인증서 파일이 실제로 있는지 먼저 확인했는지 다시 체크.

**`host not found in upstream "wks-app-dev"` 로 `nginx -t` 가 실패하면** EC2 의 `nginx/api-dev.conf` 가
resolver 적용 전(2026-09-26 이전) 판이다. 그 판은 dev 컨테이너가 떠 있어야만 통과한다. 바로 bootstrap 으로 되돌려
디스크에 깨진 설정을 남기지 않는다 — 남겨두면 nginx 가 재시작될 때 운영까지 같이 못 뜬다:

```bash
cp nginx/api-dev.bootstrap.conf nginx/api-dev.conf.active
docker compose -f docker-compose.prod.yml exec nginx nginx -t
```

그 뒤 8단계(앱 기동)를 먼저 하고 7단계를 다시 한다. resolver 판(현재 저장소)은 dev 앱이 없어도 `-t` 를 통과한다.

---

## 8. 개발 앱 기동

GHCR 이미지는 private 이라 **손으로 pull 하려면 먼저 로그인해야 한다** (`denied` 가 나면 이것). 배포 워크플로도
매번 `docker login` 을 하지만 그 `GITHUB_TOKEN` 은 잡이 끝나면 무효가 된다 — EC2 에 남은 로그인 정보로는 수동 pull 이
안 된다. GitHub 개인 토큰(classic, `read:packages` 만)으로 로그인한다. 워크플로가 다음 배포 때 이 로그인 정보를
자기 토큰으로 덮어쓰므로, 나중에 다시 손으로 pull 할 때도 다시 로그인한다.

```bash
echo <토큰> | docker login ghcr.io -u <GitHub 아이디> --password-stdin
cd /opt/wks-dev
docker compose -p wks-dev -f docker-compose.dev.yml pull
docker compose -p wks-dev -f docker-compose.dev.yml up -d
```

`No active profile set` 로 뜨고 `Failed to configure a DataSource: 'url' attribute is not specified` 로 죽으면
EC2 의 `docker-compose.dev.yml` 에 `SPRING_PROFILES_ACTIVE: dev` 가 없는 것이다 (저장소 판에는 있다).

**반드시 `-p wks-dev`를 붙인다.** 빠뜨리면 컴포즈 기본 프로젝트명(디렉토리명 `wks-dev`라 사실 같겠지만, 운영 쪽에서 실수로 이 명령을 `/opt/wks`에서 실행하면 운영 컨테이너를 덮어쓸 수 있다 — **항상 디렉토리도 함께 확인**한다.

```bash
docker ps | grep wks-app-dev
docker compose -p wks-dev -f docker-compose.dev.yml logs -f app-dev   # Flyway 마이그레이션 로그, 기동 확인
```

---

## 9. 헬스체크

```bash
curl -fsS https://api.threadoffate.site/api/health        # 운영
curl -fsS https://api-dev.threadoffate.site/api/health    # 개발
```

둘 다 200이어야 이 단계가 끝난다.

---

## 10. 롤백

### 개발 쪽만 내리기 (운영 영향 없음)

```bash
cd /opt/wks-dev
docker compose -p wks-dev -f docker-compose.dev.yml down
```

### nginx 설정 원복 (443 블록 적용 후 문제가 생겼을 때)

```bash
cd /opt/wks
cp nginx/api-dev.bootstrap.conf nginx/api-dev.conf.active   # 443 블록 빼고 80번만 남긴 상태로 되돌림
docker compose -f docker-compose.prod.yml exec nginx nginx -t
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
curl -fsS https://api.threadoffate.site/api/health           # 운영 정상 확인
```

### 2-2단계(운영 compose 변경) 롤백

변경 전 `docker-compose.prod.yml`(wks-edge 네트워크 추가 전 버전)로 되돌리고 `docker compose -f docker-compose.prod.yml up -d`. postgres·app·nginx가 다시 재생성되며 같은 규모의 다운타임(약 30~60초)이 발생한다.

---

## 11. `nginx/api-dev.conf` 가 바뀌었을 때

nginx 가 실제로 읽는 건 `nginx/api-dev.conf.active` 이고, **이 파일은 어떤 배포도 갱신하지 않는다.** `deploy.yml` 은
`main` push 때 `nginx/api-dev.conf` 를 EC2 로 보내기만 한다. 그래서 `api-dev.conf` 를 고친 PR 이 `main` 까지
릴리즈된 뒤 한 번 직접 반영한다 (2026-09-26 resolver 변경이 첫 대상):

```bash
cd /opt/wks
diff nginx/api-dev.conf nginx/api-dev.conf.active                 # 무엇이 바뀌는지 확인
cp nginx/api-dev.conf.active /tmp/api-dev.conf.active.bak          # 되돌릴 사본
cp nginx/api-dev.conf nginx/api-dev.conf.active
docker compose -f docker-compose.prod.yml exec nginx nginx -t       # ⚠️ 실패하면 .bak 으로 되돌리고 reload 안 함
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
curl -sS -o /dev/null -w "prod: %{http_code}\n" https://api.threadoffate.site/api/health
curl -sS -o /dev/null -w "dev: %{http_code}\n" https://api-dev.threadoffate.site/api/health
```

헬스체크는 `curl -fsS` 대신 위처럼 상태 코드를 찍는다. `-f` 는 실패해도 아무것도 안 찍고 넘어갈 수 있어 성공과 구분이 안 된다.

---

## 참고

- `docker compose -p wks-dev ...` 형태를 안 쓰면 운영 컨테이너를 덮어쓸 수 있다는 경고는 이 문서의 8·10단계뿐 아니라 앞으로 `/opt/wks-dev`에서 실행하는 모든 compose 명령에 적용된다
- 카카오 디벨로퍼스 개발용 앱의 Redirect URI 등록은 이 문서 범위 밖이다 (아래 "수동 작업" 참고, 메인 보고에 정리)

## Gemini 무료 프로젝트 최대 6개·유료 대체 키 설정 (#127)

키는 개발 서버 `/opt/wks-dev/.env`, 운영 서버 `/opt/wks/.env`에 넣는다. 로컬 실행은 같은 이름의 프로세스 환경변수로 전달한다(Spring Boot가 저장소의 `.env`를 자동으로 읽지는 않는다). 저장소·채팅·PR에는 실제 키를 넣지 않는다.

```dotenv
# 각각 서로 다른 무료 Google Cloud 프로젝트에서 발급한 키 하나씩
GEMINI_FREE_KEY_1=<무료 프로젝트 A 키>
GEMINI_FREE_KEY_2=<무료 프로젝트 B 키>
GEMINI_FREE_KEY_3=<무료 프로젝트 C 키>
GEMINI_FREE_KEY_4=<무료 프로젝트 D 키>
GEMINI_FREE_KEY_5=<무료 프로젝트 E 키>
GEMINI_FREE_KEY_6=<무료 프로젝트 F 키>
# 별도 유료 프로젝트. 비워 두면 유료 전환을 사용하지 않는다.
GEMINI_PAID_KEY=<유료 프로젝트 키>

# 아래는 기본값과 같다(생략 가능). 무료 실측 한도 15 RPM·500 RPD/프로젝트(2026-09-28 429 메시지 기준).
GEMINI_FREE_RPD_1=500
GEMINI_FREE_RPD_2=500
GEMINI_FREE_RPD_3=500
GEMINI_FREE_RPD_4=500
GEMINI_FREE_RPD_5=500
GEMINI_FREE_RPD_6=500
GEMINI_PAID_RPM=5
GEMINI_PAID_RPD=120
# 유료 RPD 중 사주만 쓸 수 있게 남겨 두는 호출 수. 다른 파트는 잔량이 이보다 많을 때만 쓴다
GEMINI_PAID_SAJU_RESERVE_RPD=42
GEMINI_TIMEOUT_SECONDS=8
GEMINI_TOTAL_TIMEOUT_SECONDS=25
```

- 무료 RPD 500은 Google이 429 메시지로 알려준 실제 한도(`GenerateRequestsPerDayPerProjectPerModel-FreeTier`, limit 500)다. AI Studio 한도가 바뀌면 그 값에 맞춘다. 무료 RPM도 필요하면 `GEMINI_FREE_RPM_1`·`_2`·`_3`으로 낮출 수 있다.
- 유료 예산 120/일은 운영 로그의 Google 503 비율(시도의 약 7%)을 무료 1,500/일에 적용한 값에 여유를 둔 것이다. 유료 단가는 입력 $0.30·출력 $2.50 /1M 토큰이라 호출당 약 $0.0015(호출 평균 2,400 토큰). 120회/일 전부 써도 하루 약 $0.2다.
- 무료 키가 모두 비어 있으면 기존 `GOOGLE_API_KEY` 단일 키로 동작한다. 새 무료 키를 하나라도 넣으면 기존 키를 추가로 사용하지 않는다. 프로젝트 ID는 필요 없다.
- 같은 프로젝트의 키를 여러 칸에 넣지 않는다. 동일한 키 문자열은 기동 검증에서 거부하지만, 서로 다른 키의 프로젝트 귀속은 자동 판별할 수 없다.
- dev·운영이 프로젝트를 공유하면 예산도 공유된다. 가능하면 프로젝트를 분리하고, 공유할 경우 모든 프로세스의 설정 합계를 실제 할당량 이하로 배분한다.
- 유료 키를 설정하면 무료 호출의 **Google 503·429·IO 실패(타임아웃 포함)**, 그리고 **무료 키 전부 사용 불가**일 때 과금 대체 호출을 한다(2026-09-29 변경). 유료 RPD·사주 예약분은 비용 한도가 아니라 호출 횟수 상한이다 — 금액은 단가(3.5 Flash-Lite 입력 $0.30·출력 $2.50 /1M)로 환산해 넣는다. 예: 2만 원 → `GEMINI_PAID_RPD`, 그중 사주 7천 원 → `GEMINI_PAID_SAJU_RESERVE_RPD`.
- 앱 카운터는 재시작·배포마다 0이 된다. **실제 비용 상한은 GCP 콘솔의 유료 프로젝트 일일 요청 쿼터로 건다.** 결제 예산 알림은 과금을 멈추지 않는다.
- 운영은 `docker-compose.prod.yml`의 `app.environment`에 위 변수 전달을 구현했다. 개발은 기존 `env_file`로 읽는다. `.env` 변경 후 단순 `restart`로는 새 환경변수가 적용되지 않아 컨테이너를 재생성한다. 기존 배포 이미지가 #127을 포함하는지 먼저 확인한다.

개발 서버에서 적용:

```bash
cd /opt/wks-dev
docker compose -p wks-dev -f docker-compose.dev.yml up -d --force-recreate app-dev
```

운영 서버에서 적용:

```bash
cd /opt/wks
docker compose -f docker-compose.prod.yml up -d --force-recreate app
```

로그에서는 `gemini ok/failed`의 `purpose=SAJU|COMPATIBILITY|DATING`·`project=free-1~6/paid`, `in=`·`out=`(입력·출력 토큰, 유료 한도 환산용)·`code=`, `gemini fallback`(`reason=503|429|GenAiIOException|free-unavailable`), `gemini pool unavailable. kind=free|paid`(유료는 `reservedForOthers=` 로 사주 예약분 때문에 막혔는지) 및 nginx 504를 확인한다. `code=503`이 연속이면 Google 무료 티어 과부하(`high demand`)이며 키·설정 문제가 아니다. 기동 시 `Duplicate Gemini key configuration`이면 같은 키가 두 칸에 들어간 것이다. 무료 별칭은 설정된 키의 순서다. 출력 전문이나 키가 포함될 수 있는 환경 덤프는 공유하지 않는다. 유료 호출을 중단하려면 `GEMINI_PAID_KEY`를 비우고 앱을 재생성한다. 문장 길이·DB 캐시는 변경하지 않는다.

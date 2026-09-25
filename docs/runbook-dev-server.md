# runbook-dev-server.md

EC2에서 **직접 실행**하는 문서다. 여기 적힌 명령은 이 세션이 실행하지 않는다 — 순서대로 사람이 실행한다.
⚠️ 표시가 붙은 단계는 운영(`api.threadoffate.site`)에 영향을 줄 수 있다. 트래픽이 적은 시간에 실행한다.

전제: 같은 EC2, 같은 Elastic IP에서 nginx·postgres 컨테이너 하나씩을 운영·개발이 공유한다 (`docs/architecture.md`, `docker-compose.prod.yml` 참고).

---

## 0. 사전 조건

- `api-dev.threadoffate.site` A 레코드가 Route53에 추가돼 있다 (본인 처리, 이 문서 범위 밖)
- 아래 파일들이 이미 `dev`(→`main`, 운영 쪽 파일은 `main`까지) 브랜치에 머지돼 EC2로 받을 준비가 됐다:
  `docker-compose.dev.yml`, `.env.dev.example`, `nginx/api-dev.bootstrap.conf`, `nginx/api-dev.conf`, `db/init/init-wks-dev.sql`
- **운영 compose 변경(wks-edge 네트워크 추가)은 2026-09-23 곽도윤이 diff를 확인·승인했다** (`docker-compose.prod.yml`에 이미 반영됨). EC2에 실제로 반영하는 지점이 이 문서의 2-2단계다

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

```bash
cd /opt/wks
# db/init/init-wks-dev.sql 을 EC2로 옮기고, CHANGE_ME_STRONG_PASSWORD 를 .env.dev 의 DB_PASSWORD 와
# 같은 값으로 바꾼 뒤 실행한다 (원본 파일은 플레이스홀더 그대로 유지 — 채운 사본을 커밋하지 않는다)
docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U wks -d wks -v ON_ERROR_STOP=1 -f - < /tmp/init-wks-dev.filled.sql
rm /tmp/init-wks-dev.filled.sql   # 비밀번호가 든 임시 파일은 바로 지운다
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

`docker-compose.dev.yml`은 `dev` push마다 `deploy-dev.yml`이 자동으로 여기에 scp한다 — 최초 1회는 수동으로 옮겨도 된다.

```bash
cp .env.dev.example .env          # 저장소에서 미리 받아둔 예시 파일 기준
vi .env                           # 실제 값 채우기 (DB_PASSWORD는 3단계와 동일 값, KAKAO_* 는 개발용 앱 키)
chmod 600 .env
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

```bash
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

---

## 8. 개발 앱 기동

```bash
cd /opt/wks-dev
docker compose -p wks-dev -f docker-compose.dev.yml pull
docker compose -p wks-dev -f docker-compose.dev.yml up -d
```

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

## 참고

- `docker compose -p wks-dev ...` 형태를 안 쓰면 운영 컨테이너를 덮어쓸 수 있다는 경고는 이 문서의 8·10단계뿐 아니라 앞으로 `/opt/wks-dev`에서 실행하는 모든 compose 명령에 적용된다
- 카카오 디벨로퍼스 개발용 앱의 Redirect URI 등록은 이 문서 범위 밖이다 (아래 "수동 작업" 참고, 메인 보고에 정리)

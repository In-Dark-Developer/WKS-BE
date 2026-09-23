-- wks-postgres 컨테이너 안에서 한 번만 수동 실행한다. Flyway 마이그레이션이 아니다
-- (db/migration/ 과 무관 — 스키마가 아니라 역할·DB 자체를 만드는 단계라 별도 둔다).
--
-- 실행 전에 CHANGE_ME_STRONG_PASSWORD 를 실제 비밀번호로 바꾼다. 이 값은 .env.dev.example 의
-- DB_PASSWORD 와 같아야 한다. 실행 후 이 파일을 값 채운 채로 커밋하지 않는다 — 커밋된 버전은
-- 항상 플레이스홀더 그대로 유지한다.
--
-- 실행 예시 (docs/runbook-dev-server.md 3단계):
--   docker compose -f docker-compose.prod.yml exec -T postgres \
--     psql -U wks -d wks -v ON_ERROR_STOP=1 -f - < db/init/init-wks-dev.sql

CREATE ROLE wks_dev WITH LOGIN PASSWORD 'CHANGE_ME_STRONG_PASSWORD';

CREATE DATABASE wks_dev OWNER wks_dev;

-- wks_dev가 운영 DB(wks)에 접속하지 못하게 막는다. PostgreSQL은 기본적으로 모든 로그인 역할이
-- 모든 DB에 CONNECT 권한을 갖는다(PUBLIC GRANT) — 명시적으로 걷어내지 않으면 같은 인스턴스를
-- 공유하는 의미가 없다.
REVOKE CONNECT ON DATABASE wks FROM PUBLIC;
GRANT CONNECT ON DATABASE wks TO wks;

REVOKE CONNECT ON DATABASE wks_dev FROM PUBLIC;
GRANT CONNECT ON DATABASE wks_dev TO wks_dev;

-- 주의: 이 REVOKE는 wks_dev → wks 방향만 막는다. wks는 이 postgres 컨테이너(POSTGRES_USER로
-- 초기화된 계정)에서 슈퍼유저라 REVOKE/권한 체계 자체를 우회하므로 wks → wks_dev 접속은 막을 수
-- 없다(로컬 postgres:16 컨테이너로 직접 검증함, 2026-09-23). 우선순위가 높은 방향(개발 환경/키가
-- 새어나가도 실사용자 개인정보가 든 운영 DB를 못 읽는 것)은 지켜진다. wks 자체가 wks_dev를
-- 건드리지 못하게 하려면 wks를 슈퍼유저가 아닌 일반 역할로 재구성해야 하는데, 이는 운영 DB
-- 초기화 방식을 바꾸는 별도 작업이라 이 스크립트 범위 밖이다.

-- 확인: 아래 명령이 "FATAL: permission denied for database"로 실패해야 정상이다
--   docker compose -f docker-compose.prod.yml exec postgres psql -U wks_dev -d wks -c '\conninfo'
-- (반대 방향 docker compose ... exec postgres psql -U wks -d wks_dev 는 위 이유로 성공한다 — 정상)

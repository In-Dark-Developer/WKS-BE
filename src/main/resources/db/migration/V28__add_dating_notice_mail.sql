-- 소개팅 프로필 보유자 대상 일괄 안내 메일의 수신자별 발송 기록 (#159).
-- (campaign_key, profile_id) UNIQUE 가 중복 발송을 막는다 — 두 번 실행하거나 동시에 눌러도 같은 사람에게 한 번만 간다.
-- 발송 직전에 SENDING 으로 선점하고, 결과에 따라 SENT/FAILED 로 바꾼다. FAILED 만 다시 선점할 수 있다.
CREATE TABLE dating_notice_mail (
    id           BIGSERIAL PRIMARY KEY,
    campaign_key VARCHAR(50) NOT NULL,
    profile_id   UUID NOT NULL REFERENCES dating_profile(id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (campaign_key, profile_id)
);

-- 예약 발송. 앱이 1분마다 send_at 이 지난 SCHEDULED 행을 STARTED 로 바꾸며(조건부 UPDATE 라 한 번만 잡힌다) 발송한다.
-- 재기동 중에 시각이 지나도 뜬 뒤 다음 확인에서 보낸다. 1시간 넘게 늦었으면 보내지 않고 EXPIRED 로 둔다.
CREATE TABLE dating_notice_campaign (
    campaign_key VARCHAR(50) PRIMARY KEY,
    subject      VARCHAR(200) NOT NULL,
    body         TEXT NOT NULL,
    send_at      TIMESTAMPTZ NOT NULL,
    state        VARCHAR(10) NOT NULL,
    started_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

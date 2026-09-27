-- 실(재화) 원장 (plan.md §1.4·§9.4). 잔액 컬럼을 두지 않고 SUM(amount)로 계산한다.
-- V12로 예약돼 있었으나 V13~V18이 먼저 머지되어(dev에 이미 적용됨) 그 번호를 쓰면 기존 환경에서
-- out-of-order 오류가 난다. V12 예약은 폐기하고 V19로 새로 받는다 (docs/handoff.md 참고).
CREATE TABLE thread_ledger (
    id          BIGSERIAL    PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    amount      INTEGER      NOT NULL,
    reason      VARCHAR(20)  NOT NULL
                 CHECK (reason IN ('SIGNUP_BONUS', 'CHECK_IN', 'MAP_FRIEND', 'PARTNER', 'UNLOCK', 'REQUEST', 'REROLL')),
    ref_id      VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 중복 지급·차감 방지 (FR-TH-02). ref_id 는 NOT NULL — NULL 끼리는 Postgres 가 다르다고 보므로
-- NULL 을 허용하면 UNIQUE 가 중복을 못 막는다.
CREATE UNIQUE INDEX uq_thread_ledger_dedup ON thread_ledger(member_id, reason, ref_id);
CREATE INDEX idx_thread_ledger_member ON thread_ledger(member_id);

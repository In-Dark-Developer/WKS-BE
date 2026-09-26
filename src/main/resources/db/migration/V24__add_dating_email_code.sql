-- 소개팅 학교메일 6자리 코드 인증. 프로필 등록 "전"에 인증하므로 dating_profile 이 아니라 회원에 묶는다
-- (V21 dating_email_verification 은 프로필이 있어야 발급돼서 이 흐름에 못 쓴다).
-- 회원당 최근 발송분 한 행만 둔다 — 재발송하면 덮어쓰고, 이전 코드는 그 순간 무효가 된다.
CREATE TABLE dating_email_code (
    member_id         BIGINT       PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
    email             VARCHAR(255) NOT NULL,
    -- 평문 코드를 남기지 않는다 (SHA-256 hex)
    code_hash         VARCHAR(64)  NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    failed_attempts   INT          NOT NULL DEFAULT 0,
    last_sent_at      TIMESTAMPTZ  NOT NULL,
    -- 발송 남용(메일 폭탄·SMTP 한도 소진) 방지용 24시간 창
    send_window_start TIMESTAMPTZ  NOT NULL,
    send_count        INT          NOT NULL,
    verified_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

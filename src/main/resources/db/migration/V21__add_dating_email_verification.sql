-- 소개팅 학교메일 재학 인증 매직링크 (TBD-16). signup/email_verification 과 같은 구조지만
-- dating_profile 을 참조한다 — signup 은 로그인 개념이 생기기 전 스키마라 재사용하지 않는다.
CREATE TABLE dating_email_verification (
    token             VARCHAR(64) PRIMARY KEY,
    dating_profile_id UUID NOT NULL REFERENCES dating_profile(id) ON DELETE CASCADE,
    expires_at        TIMESTAMPTZ NOT NULL,
    used_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dating_email_verification_profile ON dating_email_verification(dating_profile_id);

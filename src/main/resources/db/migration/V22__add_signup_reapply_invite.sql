-- 기존 사전신청자(signup)에게 "사진·학교메일 인증을 미리 마무리해라"고 보내는 초대 토큰.
-- email_verification 을 재사용하지 않은 이유: 수명(30분 vs 48시간)과 소비 시점이 다르다 —
-- 이 토큰은 클릭 시점이 아니라 소개팅 프로필 생성이 끝날 때 소비된다(카카오 로그인·사진 업로드를
-- 거치는 다단계 흐름 동안 살아 있어야 한다).
-- 축제(2026-10-01) 뒤에는 이 테이블과 signup/ 재신청 코드를 통째로 버려도 된다.
CREATE TABLE signup_reapply_invite (
    token      VARCHAR(64) PRIMARY KEY,
    signup_id  BIGINT NOT NULL REFERENCES signup(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_signup_reapply_invite_signup ON signup_reapply_invite(signup_id);

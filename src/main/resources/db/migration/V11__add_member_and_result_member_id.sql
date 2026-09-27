-- 카카오 로그인 (2026-09-21 설계, 2026-09-22 구현). 카카오 프로필(닉네임·이메일 등)은 저장하지 않는다.
CREATE TABLE member (
    id              BIGSERIAL PRIMARY KEY,
    kakao_id        BIGINT       NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 로그인 회원이 "저장"한 사주 결과. result 는 그 외엔 회원을 모른다 (architecture.md §4).
-- 계정당 결과 1개 — 병합은 V2 과제(plan.md §1.1). member_id NULL 은 익명 결과.
ALTER TABLE result ADD COLUMN member_id BIGINT REFERENCES member(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_result_member ON result(member_id) WHERE member_id IS NOT NULL;

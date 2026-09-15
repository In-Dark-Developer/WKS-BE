-- 필수/선택 여부가 기획 미확정 상태(2026-09-15 기준)라 전부 nullable로 추가한다.
-- 결정되면 후속 마이그레이션에서 해당 컬럼에 NOT NULL 제약을 건다.
ALTER TABLE signup
    ADD COLUMN name           VARCHAR(50),
    ADD COLUMN contact_method VARCHAR(10),
    ADD COLUMN contact_value  VARCHAR(100),
    ADD COLUMN department     VARCHAR(100),
    ADD COLUMN mbti           VARCHAR(4),
    ADD COLUMN bio            VARCHAR(500);

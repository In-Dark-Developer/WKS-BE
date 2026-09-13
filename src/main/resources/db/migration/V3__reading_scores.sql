-- 등급 문자열 대신 0~100 점수를 저장한다. 등급은 응답 시 코드(Grade.of)가 계산한다.
-- 아직 프로덕션 배포 전이라 기존 등급 값은 버린다.
ALTER TABLE reading
    DROP COLUMN marriage_grade,
    DROP COLUMN children_grade,
    DROP COLUMN love_grade,
    ADD COLUMN marriage_score SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN children_score SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN love_score     SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE reading
    ALTER COLUMN marriage_score DROP DEFAULT,
    ALTER COLUMN children_score DROP DEFAULT,
    ALTER COLUMN love_score     DROP DEFAULT;

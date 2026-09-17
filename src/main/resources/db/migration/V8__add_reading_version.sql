-- 같은 입력(생년월일·시간·성별)의 해석 재사용용 버전 (#62).
-- 프롬프트·점수 로직 버전. 값이 다르면 재사용하지 않는다. 기존 행은 0 = 재사용 대상 아님.
ALTER TABLE reading ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_result_birth_gender ON result (birth_date, birth_time, gender);

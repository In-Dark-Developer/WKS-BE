-- 입력 폼 자동 채움용 원본 입력값 (#66).
-- birth_date 는 양력 변환값이라 음력 입력을 복원할 수 없었다. 입력 그대로 따로 저장한다.
ALTER TABLE result ADD COLUMN calendar_type    VARCHAR(10) NOT NULL DEFAULT 'SOLAR';
ALTER TABLE result ADD COLUMN birth_date_input VARCHAR(10);
ALTER TABLE result ADD COLUMN is_leap_month    BOOLEAN     NOT NULL DEFAULT false;

-- 기존 행은 전부 양력 입력으로 본다 (음력 원본은 남아 있지 않다)
UPDATE result SET birth_date_input = to_char(birth_date, 'YYYY-MM-DD') WHERE birth_date_input IS NULL;

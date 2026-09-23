-- 사진은 선택값이라 nullable로 추가한다. S3 오브젝트 키만 저장하고, 실제 파일은 S3에 있다.
ALTER TABLE signup
    ADD COLUMN photo_key VARCHAR(255);

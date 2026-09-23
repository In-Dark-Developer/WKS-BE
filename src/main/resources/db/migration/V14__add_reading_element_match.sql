-- "나와 잘 맞는 오행" 풀이 (기능명세 3.5, #82). 오행 자체는 팔자에서 계산하므로 문장만 저장한다.
-- 기존 행은 NULL 로 두고 응답에서 영역을 빼며, 프롬프트 버전이 바뀌어 같은 입력도 다시 생성된다 (#62).
ALTER TABLE reading
    ADD COLUMN element_match_content TEXT;

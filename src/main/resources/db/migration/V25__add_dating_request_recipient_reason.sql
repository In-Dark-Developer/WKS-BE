-- 받은 사람 기준 궁합 이유 캐시 (#123). 추천 행의 reason_content 는 보낸 사람 시점 문장이라 따로 둔다.
ALTER TABLE dating_request ADD COLUMN recipient_reason TEXT;

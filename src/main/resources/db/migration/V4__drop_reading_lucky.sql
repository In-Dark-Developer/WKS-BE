-- 행운 아이템·장소는 저장하지 않는다. 조회 시점에 팔자 + 오늘 일진으로 계산한다 (매일 바뀜).
ALTER TABLE reading
    DROP COLUMN lucky_item,
    DROP COLUMN lucky_place;

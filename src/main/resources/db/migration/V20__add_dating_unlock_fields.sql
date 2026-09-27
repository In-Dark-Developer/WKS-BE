-- 소개팅 카드 정보 해금 (plan.md §8.5·§9.1). 잠금 여부는 (조회자, 후보) 쌍인 이 행에 저장한다 —
-- 한 번 추천된 후보는 다시 추천되지 않으므로(리롤 전까지 이 행 재사용 안전) 별도 해금 테이블이 필요 없다.
ALTER TABLE dating_recommendation
    ADD COLUMN photo_unlocked      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN name_unlocked       BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN department_unlocked BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN reason_unlocked     BOOLEAN NOT NULL DEFAULT false;

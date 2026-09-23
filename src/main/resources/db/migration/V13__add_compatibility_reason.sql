-- 궁합 상세 이유 캐시 (plan §1.2). 처음 열어볼 때 LLM 으로 생성해 저장하고, 이후 조회는 LLM 호출 0회.
-- 세 답은 한 번의 호출로 함께 생기므로 셋 다 NULL 이거나 셋 다 값이 있다.
ALTER TABLE compatibility
    ADD COLUMN reason_why      TEXT,
    ADD COLUMN reason_together TEXT,
    ADD COLUMN reason_conflict TEXT;

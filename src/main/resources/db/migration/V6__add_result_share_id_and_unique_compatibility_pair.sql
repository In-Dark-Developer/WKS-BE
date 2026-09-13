-- resultId는 내부 결과 조회에, shareId는 친구에게 공개하는 링크에 사용한다.
ALTER TABLE result
    ADD COLUMN share_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX uq_result_share_id ON result(share_id);

-- A↔B와 B↔A를 같은 궁합 조합으로 취급한다.
CREATE UNIQUE INDEX uq_compatibility_result_pair
    ON compatibility (LEAST(origin_id, guest_id), GREATEST(origin_id, guest_id));

CREATE INDEX idx_compat_guest ON compatibility(guest_id);

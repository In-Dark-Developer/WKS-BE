-- 이 결과를 만든(또는 로그인하며 자기 것이라고 제시한) 계정 (2026-09-30).
-- member_id 는 계정의 대표 결과(계정당 1개, plan.md §1.1 계정 우선)라 계정에 결과가 이미 있으면 새 결과는
-- member_id 가 비고, 그 결과로 남긴 궁합지도 별이 누구 것인지 알 수 없어 친구 보상(+2)이 영구히 빠졌다.
-- 보상 판단은 COALESCE(member_id, claimed_member_id) 로 하고, 대표 결과·복원 규칙은 member_id 그대로 둔다.
ALTER TABLE result ADD COLUMN claimed_member_id BIGINT REFERENCES member(id) ON DELETE SET NULL;

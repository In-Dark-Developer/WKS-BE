-- 운영자가 소개팅 프로필을 잠시 내리는 상태 (#147). NULL 이면 정상.
-- 자격 판정(DatingProfile.isEligible)에 verified_at 과 함께 쓰여 추천 풀·기존 카드·수락에 한 번에 반영된다.
ALTER TABLE dating_profile ADD COLUMN deactivated_at TIMESTAMPTZ;

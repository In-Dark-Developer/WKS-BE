package com.darkness.wks.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 축제 기간(KST) 통계. 운영자용이라 프론트 계약(api-spec)이 아니다. 개인 단위 값은 없고 개수만 담는다.
 * 분포 맵은 enum 순서대로 0 을 포함해 모든 키를 채운다 — 페이지가 키 존재를 따로 검사하지 않게.
 */
public record AdminStatsResponse(
        String timezone,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        Saju saju,
        Compatibility compatibility,
        Dating dating
) {
    /** hourly 는 KST 0~23시, 항상 24칸. */
    public record DailyCount(LocalDate date, long count, List<Long> hourly) {
    }

    public record Series(long total, List<DailyCount> days) {
    }

    public record Saju(Series results, Map<String, Long> byGender) {
    }

    public record Compatibility(Series created, Map<String, Long> byTier) {
    }

    /**
     * profiles·requests 는 기간 안에 만들어진 것 기준이고, 상태(인증·비활성·요청 status)는 조회 시점 값이다.
     * recommendationViewers 는 기간 안에 추천 카드를 한 번이라도 받은 회원 수.
     *
     * <p>성별은 프로필에 없어 회원의 사주 결과(result.gender)에서 온다. 결과가 없으면 UNKNOWN 으로 센다(있을 때만 키가 생긴다).
     * poolByGender 만 예외로 기간과 무관한 "지금 추천 풀"(인증·비활성 아님) 전체다 — 축제 전 사전 등록자도 추천에
     * 나오므로 성비 불균형은 풀 전체로 봐야 한다.
     *
     * <p>accepted 는 기간 안에 수락된(responded_at) 요청 시리즈 — 요청은 축제 전에 왔어도 된다.
     * acceptedMembers 는 그 요청의 양쪽 프로필 수(중복 제거). requestsBySenderGender 는 기간 안에 만든 요청을 보낸 쪽 성별로 나눈 것.
     */
    public record Dating(
            Series profiles,
            long verifiedProfiles,
            long deactivatedProfiles,
            long recommendationViewers,
            Map<String, Long> profilesByGender,
            Map<String, Long> poolByGender,
            Series requests,
            Map<String, Long> requestsByStatus,
            Map<String, RequestOutcome> requestsBySenderGender,
            Series accepted,
            long acceptedMembers
    ) {
    }

    public record RequestOutcome(long sent, long accepted, long rejected, long pending) {
    }
}

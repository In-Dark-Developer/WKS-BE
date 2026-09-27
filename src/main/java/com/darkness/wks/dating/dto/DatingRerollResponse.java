package com.darkness.wks.dating.dto;

import com.darkness.wks.dating.dto.DatingRecommendationResponse.CandidateCard;

import java.util.List;

/**
 * @param candidates    리롤로 새로 뽑힌 카드. 새 후보가 3명 미만이면 그 수만큼만 온다
 * @param rerollCost    다음 리롤 비용(실). {@link DatingRecommendationResponse#rerollCost()} 와 같은 의미
 * @param threadBalance 차감 후 잔액
 */
public record DatingRerollResponse(List<CandidateCard> candidates, int rerollCost, int threadBalance) {
}

package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.SajuPillars;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 해금 API가 결제를 확정한 뒤 호출한다. 추천 조회에서는 호출하지 않는다. */
@Service
public class DatingReasonService {

    private final DatingRecommendationRepository recommendationRepository;
    private final ResultRepository resultRepository;
    private final DatingReasonGenerator generator;

    public DatingReasonService(DatingRecommendationRepository recommendationRepository,
                               ResultRepository resultRepository, DatingReasonGenerator generator) {
        this.recommendationRepository = recommendationRepository;
        this.resultRepository = resultRepository;
        this.generator = generator;
    }

    public String getOrCreate(Long viewerMemberId, UUID candidateId) {
        DatingRecommendation recommendation = recommendationRepository
                .findActiveWithCandidate(viewerMemberId, candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
        if (recommendation.getReasonContent() != null) {
            return recommendation.getReasonContent();
        }

        Result viewer = resultRepository.findByMemberId(viewerMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        Result candidate = resultRepository.findByMemberId(recommendation.getCandidate().getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        String generated = generator.generate(pillars(viewer), pillars(candidate),
                recommendation.getScore(), CompatibilityTier.fromScore(recommendation.getScore()).korean());
        if (recommendationRepository.saveReasonIfAbsent(recommendation.getId(), generated) == 1) {
            return generated;
        }
        // 동시 생성 시 먼저 저장된 문장을 반환한다. LLM 호출 중에는 DB 트랜잭션을 열어 두지 않는다.
        return recommendationRepository.findReasonContentById(recommendation.getId())
                .orElseThrow(() -> new IllegalStateException("dating reason cache missing after concurrent write"));
    }

    private static SajuPillars pillars(Result result) {
        return new SajuPillars(result.getYearPillar(), result.getMonthPillar(),
                result.getDayPillar(), result.getHourPillar());
    }
}

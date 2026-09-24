package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingRecommendationResponse;
import com.darkness.wks.dating.dto.DatingRecommendationResponse.CandidateCard;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.SajuPillars;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DatingRecommendationService {

    private final EntityManager entityManager;
    private final DatingProfileRepository profileRepository;
    private final ResultRepository resultRepository;
    private final DatingRecommendationRepository recommendationRepository;
    private final DatingRecommendationSelector selector;

    public DatingRecommendationService(EntityManager entityManager, DatingProfileRepository profileRepository,
                                       ResultRepository resultRepository,
                                       DatingRecommendationRepository recommendationRepository,
                                       DatingRecommendationSelector selector) {
        this.entityManager = entityManager;
        this.profileRepository = profileRepository;
        this.resultRepository = resultRepository;
        this.recommendationRepository = recommendationRepository;
        this.selector = selector;
    }

    @Transactional
    public DatingRecommendationResponse getCurrent(Long memberId) {
        // 같은 회원의 동시 최초 조회가 같은 후보를 두 번 기록하지 않도록 회원 행을 직렬화한다.
        if (entityManager.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        DatingProfile viewer = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
        if (viewer.getVerifiedAt() == null) {
            throw new BusinessException(ErrorCode.DATING_NOT_VERIFIED);
        }
        if (viewer.getMatchedAt() != null) {
            return new DatingRecommendationResponse(List.of());
        }
        Result viewerResult = resultRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));

        List<DatingRecommendation> history = recommendationRepository.findAllByViewerMemberId(memberId);
        List<DatingRecommendation> active = history.stream()
                .filter(DatingRecommendation::isActive)
                .filter(recommendation -> recommendation.getCandidate().isEligible())
                .toList();
        history.stream().filter(DatingRecommendation::isActive)
                .filter(recommendation -> !recommendation.getCandidate().isEligible())
                .forEach(DatingRecommendation::deactivate);

        if (active.size() < 3) {
            List<DatingProfile> pool = profileRepository.findEligible();
            Map<UUID, DatingProfile> byId = pool.stream()
                    .collect(Collectors.toMap(DatingProfile::getId, Function.identity()));
            Map<Long, Result> resultsByMemberId = pool.isEmpty() ? Map.of()
                    : resultRepository.findAllByMemberIdIn(pool.stream().map(DatingProfile::getMemberId).toList())
                    .stream().collect(Collectors.toMap(Result::getMemberId, Function.identity()));
            Set<UUID> shownIds = history.stream().map(item -> item.getCandidate().getId())
                    .collect(Collectors.toSet());
            List<DatingRecommendationSelector.Candidate> candidates = pool.stream()
                    .filter(profile -> resultsByMemberId.containsKey(profile.getMemberId()))
                    .map(profile -> new DatingRecommendationSelector.Candidate(profile.getId(),
                            resultsByMemberId.get(profile.getMemberId()).getGender(),
                            pillars(resultsByMemberId.get(profile.getMemberId())), profile.isEligible()))
                    .toList();
            List<DatingRecommendation> additions = selector.select(viewer.getId(),
                            viewerResult.getGender(), pillars(viewerResult),
                            candidates, shownIds, 3 - active.size()).stream()
                    .map(choice -> new DatingRecommendation(memberId, byId.get(choice.profileId()), choice.score()))
                    .toList();
            active = new java.util.ArrayList<>(active);
            active.addAll(recommendationRepository.saveAll(additions));
        }

        List<DatingRecommendation> ordered = active.stream()
                .sorted(Comparator.comparingInt(DatingRecommendation::getScore).reversed()
                        .thenComparing(item -> item.getCandidate().getId()))
                .toList();
        List<CandidateCard> cards = java.util.stream.IntStream.range(0, ordered.size())
                .mapToObj(index -> CandidateCard.from(index + 1, ordered.get(index)))
                .toList();
        return new DatingRecommendationResponse(cards);
    }

    private static SajuPillars pillars(Result result) {
        return new SajuPillars(result.getYearPillar(), result.getMonthPillar(),
                result.getDayPillar(), result.getHourPillar());
    }
}

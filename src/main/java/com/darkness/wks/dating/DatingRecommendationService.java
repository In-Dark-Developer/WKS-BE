package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingRecommendationResponse;
import com.darkness.wks.dating.dto.DatingRecommendationResponse.CandidateCard;
import com.darkness.wks.dating.dto.DatingRerollResponse;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.SajuPillars;
import com.darkness.wks.wallet.LedgerReason;
import com.darkness.wks.wallet.WalletService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DatingRecommendationService {

    private static final int CARD_COUNT = 3;
    // TBD-6 종료 (2026-09-27): KST 날짜 기준 하루 1회 무료, 이후 회당 5실. 출석 체크와 같은 날짜 기준이다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int FREE_REROLLS_PER_DAY = 1;
    private static final int REROLL_COST = 5;

    private final EntityManager entityManager;
    private final DatingProfileRepository profileRepository;
    private final ResultRepository resultRepository;
    private final DatingRecommendationRepository recommendationRepository;
    private final DatingRecommendationSelector selector;
    private final DatingPhotoService photoService;
    private final WalletService walletService;

    public DatingRecommendationService(EntityManager entityManager, DatingProfileRepository profileRepository,
                                       ResultRepository resultRepository,
                                       DatingRecommendationRepository recommendationRepository,
                                       DatingRecommendationSelector selector, DatingPhotoService photoService,
                                       WalletService walletService) {
        this.entityManager = entityManager;
        this.profileRepository = profileRepository;
        this.resultRepository = resultRepository;
        this.recommendationRepository = recommendationRepository;
        this.selector = selector;
        this.photoService = photoService;
        this.walletService = walletService;
    }

    @Transactional
    public DatingRecommendationResponse getCurrent(Long memberId) {
        Viewer viewer = lockAndLoadViewer(memberId);
        List<DatingRecommendation> history = recommendationRepository.findAllByViewerMemberId(memberId);
        List<DatingRecommendation> active = new ArrayList<>(activeEligible(history));
        if (active.size() < CARD_COUNT) {
            active.addAll(recommendationRepository.saveAll(
                    pickNew(memberId, viewer, history, CARD_COUNT - active.size())));
        }
        return new DatingRecommendationResponse(cards(active), costOf(rerollsToday(memberId)));
    }

    /**
     * 현재 카드를 전부 내리고 한 번도 안 나온 후보로 새로 뽑는다 (2026-09-27 결정 — 해금·요청 중인 카드도
     * 내린다. 보낸 요청은 카드가 없어도 요청 목록에서 계속 보인다, plan.md §8.6). 새 후보가 한 명도
     * 없으면 차감하지 않고 {@link ErrorCode#DATING_NO_MORE_CANDIDATES} — 빈 카드에 실을 받지 않는다.
     * 선정·차감·교체가 한 트랜잭션이라 잔액 부족(402)이면 카드도 그대로 남는다.
     */
    @Transactional
    public DatingRerollResponse reroll(Long memberId) {
        Viewer viewer = lockAndLoadViewer(memberId);
        List<DatingRecommendation> history = recommendationRepository.findAllByViewerMemberId(memberId);
        List<DatingRecommendation> fresh = pickNew(memberId, viewer, history, CARD_COUNT);
        if (fresh.isEmpty()) {
            throw new BusinessException(ErrorCode.DATING_NO_MORE_CANDIDATES);
        }

        // ref_id 는 "KST날짜#회차". 회원 행 잠금으로 같은 회원의 리롤이 직렬화되므로 회차가 겹치지 않고,
        // 겹치더라도 원장 UNIQUE 가 막는다. 연타는 막지 않는다 — 두 번 누르면 두 번 리롤된다.
        int used = rerollsToday(memberId);
        walletService.debit(memberId, LedgerReason.REROLL, today() + "#" + (used + 1), costOf(used));

        history.stream().filter(DatingRecommendation::isActive).forEach(DatingRecommendation::deactivate);
        List<DatingRecommendation> saved = recommendationRepository.saveAll(fresh);
        return new DatingRerollResponse(cards(saved), costOf(used + 1), walletService.getBalance(memberId));
    }

    private Viewer lockAndLoadViewer(Long memberId) {
        // 같은 회원의 동시 조회·리롤이 같은 후보를 두 번 기록하거나 리롤 회차가 겹치지 않도록 회원 행을 직렬화한다.
        if (entityManager.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        DatingProfile profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
        if (profile.getVerifiedAt() == null) {
            throw new BusinessException(ErrorCode.DATING_NOT_VERIFIED);
        }
        Result result = resultRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        return new Viewer(profile, result);
    }

    /** 활성 카드 중 아직 자격이 있는 것만 돌려주고, 자격을 잃은 카드는 내린다. */
    private static List<DatingRecommendation> activeEligible(List<DatingRecommendation> history) {
        history.stream().filter(DatingRecommendation::isActive)
                .filter(recommendation -> !recommendation.getCandidate().isEligible())
                .forEach(DatingRecommendation::deactivate);
        return history.stream().filter(DatingRecommendation::isActive).toList();
    }

    /** 한 번도 카드에 나온 적 없는 후보 중 최대 {@code limit} 명. 저장하지 않은 엔티티를 돌려준다. */
    private List<DatingRecommendation> pickNew(Long memberId, Viewer viewer,
                                               List<DatingRecommendation> history, int limit) {
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
        return selector.select(viewer.profile().getId(), viewer.result().getGender(),
                        pillars(viewer.result()), candidates, shownIds, limit).stream()
                .map(choice -> new DatingRecommendation(memberId, byId.get(choice.profileId()), choice.score()))
                .toList();
    }

    private List<CandidateCard> cards(List<DatingRecommendation> recommendations) {
        List<DatingRecommendation> ordered = recommendations.stream()
                .sorted(Comparator.comparingInt(DatingRecommendation::getScore).reversed()
                        .thenComparing(item -> item.getCandidate().getId()))
                .toList();
        return IntStream.range(0, ordered.size())
                .mapToObj(index -> {
                    DatingRecommendation item = ordered.get(index);
                    String unlockedPhotoUrl = item.isPhotoUnlocked()
                            ? photoService.originalUrl(item.getCandidate().getPhoto()) : null;
                    return CandidateCard.from(index + 1, item,
                            photoService.thumbnailUrl(item.getCandidate().getPhoto()), unlockedPhotoUrl);
                })
                .toList();
    }

    private int rerollsToday(Long memberId) {
        return walletService.countEntries(memberId, LedgerReason.REROLL, today() + "#");
    }

    private static int costOf(int rerollsAlreadyUsedToday) {
        return rerollsAlreadyUsedToday < FREE_REROLLS_PER_DAY ? 0 : REROLL_COST;
    }

    private static String today() {
        return LocalDate.now(KST).toString();
    }

    private static SajuPillars pillars(Result result) {
        return new SajuPillars(result.getYearPillar(), result.getMonthPillar(),
                result.getDayPillar(), result.getHourPillar());
    }

    private record Viewer(DatingProfile profile, Result result) {
    }
}

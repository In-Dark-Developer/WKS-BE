package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.dating.DatingRequestNotifier.DatingRequestSentEvent;
import com.darkness.wks.dating.DatingRequestNotifier.DatingRequestAcceptedEvent;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.dating.entity.DatingRequest;
import com.darkness.wks.dating.entity.DatingRequestStatus;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.SajuPillars;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 소개팅 궁합 이유 두 가지.
 * <ul>
 *   <li>{@link #getOrCreate}: 추천 카드의 이유(보낸 사람 시점). 해금 API가 결제를 확정하기 전에 호출한다.
 *       수락된 보낸 요청에서는 같은 문장을 비동기로 무료 생성한다. 추천 조회에서는 호출하지 않는다.</li>
 *   <li>{@link #fillRecipientReason}: 받은 요청 목록의 이유(받은 사람 시점, #123). 요청이 커밋된 뒤 별도
 *       스레드에서 한 번 만든다 — 요청 응답·트랜잭션을 LLM(최대 30초)이 붙잡지 않게.</li>
 * </ul>
 */
@Slf4j
@Service
public class DatingReasonService {

    private final DatingRecommendationRepository recommendationRepository;
    private final DatingRequestRepository requestRepository;
    private final ResultRepository resultRepository;
    private final DatingReasonGenerator generator;
    private final TaskExecutor taskExecutor;
    private final Clock clock;
    // 같은 요청의 생성이 겹치지 않게 하고, 실패 뒤 60초 안의 목록 조회가 LLM을 다시 부르지 않게 한다.
    // 저장은 DB 가 한 번만 받지만 LLM 호출은 그 전에 나가므로 여기서 막는다
    private final Map<Job, Instant> nextRetryAt = new HashMap<>();
    private final Set<Job> inFlight = new HashSet<>();

    /** 받은 사람 문장과 보낸 사람 문장은 저장 위치가 달라 각각 따로 진행·쿨다운한다. */
    private record Job(UUID requestId, boolean sender) {
    }

    @Autowired
    public DatingReasonService(DatingRecommendationRepository recommendationRepository,
                               DatingRequestRepository requestRepository, ResultRepository resultRepository,
                               DatingReasonGenerator generator,
                               @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this(recommendationRepository, requestRepository, resultRepository, generator, taskExecutor, Clock.systemUTC());
    }

    DatingReasonService(DatingRecommendationRepository recommendationRepository,
                        DatingRequestRepository requestRepository, ResultRepository resultRepository,
                        DatingReasonGenerator generator, TaskExecutor taskExecutor, Clock clock) {
        this.recommendationRepository = recommendationRepository;
        this.requestRepository = requestRepository;
        this.resultRepository = resultRepository;
        this.generator = generator;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    public String getOrCreate(Long viewerMemberId, UUID candidateId) {
        DatingRecommendation recommendation = recommendationRepository
                .findActiveWithCandidate(viewerMemberId, candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
        if (recommendation.getReasonContent() != null) {
            return recommendation.getReasonContent();
        }

        return generateAndSave(recommendation, viewerMemberId, recommendation.getCandidate().getMemberId());
    }

    private String generateAndSave(DatingRecommendation recommendation, Long viewerMemberId,
                                   Long candidateMemberId) {
        Result viewer = resultRepository.findByMemberId(viewerMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        Result candidate = resultRepository.findByMemberId(candidateMemberId)
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

    /** 커밋 뒤에만 — 롤백된 요청(중복·동시 충돌)의 이유를 만들어 LLM 한도를 태우지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestSent(DatingRequestSentEvent event) {
        fillRecipientReasonAsync(event.requestId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestAccepted(DatingRequestAcceptedEvent event) {
        fillSenderReasonAsync(event.requestId());
    }

    /** 수락 후 보낸 사람 목록의 무료 이유. 추천 카드가 리롤로 비활성화돼도 요청에 연결된 행을 쓴다. */
    public void fillSenderReasonAsync(UUID requestId) {
        schedule(new Job(requestId, true), () -> fillSenderReason(requestId));
    }

    boolean fillSenderReason(UUID requestId) {
        try {
            DatingRequest request = requestRepository.findWithProfiles(requestId).orElseThrow();
            if (request.getStatus() != DatingRequestStatus.ACCEPTED) {
                return true;
            }
            Long senderMemberId = request.getSender().getMemberId();
            DatingRecommendation recommendation = recommendationRepository.findForRequestPairs(
                    List.of(senderMemberId), List.of(request.getRecipient().getId()))
                    .stream().findFirst().orElseThrow();
            if (recommendation.getReasonContent() == null) {
                generateAndSave(recommendation, senderMemberId, request.getRecipient().getMemberId());
            }
            return true;
        } catch (RuntimeException exception) {
            log.warn("dating sender reason failed. requestId={}, cause={}", requestId,
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /** 받은 목록 조회에서 아직 이유가 없는 요청을 다시 시도할 때도 부른다. 응답은 기다리지 않는다. */
    public void fillRecipientReasonAsync(UUID requestId) {
        schedule(new Job(requestId, false), () -> fillRecipientReason(requestId));
    }

    /** 진행 중이거나 실패 후 60초가 안 지났으면 건너뛴다. 실행 거절도 실패로 본다. */
    private void schedule(Job job, BooleanSupplier work) {
        if (!begin(job)) {
            return;
        }
        try {
            taskExecutor.execute(() -> {
                boolean success = false;
                try {
                    success = work.getAsBoolean();
                } finally {
                    finish(job, success);
                }
            });
        } catch (RuntimeException rejected) {
            finish(job, false);
            log.warn("dating reason not scheduled. requestId={}, sender={}, cause={}", job.requestId(), job.sender(),
                    rejected.getClass().getSimpleName());
        }
    }

    private synchronized boolean begin(Job job) {
        Instant now = clock.instant();
        // ponytail: 축제 요청 수에서는 순회 정리로 충분. 장기 운영 시 만료 캐시로 교체한다.
        nextRetryAt.values().removeIf(at -> !at.isAfter(now));
        return !nextRetryAt.containsKey(job) && inFlight.add(job);
    }

    private synchronized void finish(Job job, boolean success) {
        if (success) nextRetryAt.remove(job);
        else nextRetryAt.put(job, clock.instant().plusSeconds(60));
        inFlight.remove(job);
    }

    /**
     * 받은 사람(viewer)이 보낸 사람(candidate)을 보는 문장. 점수는 보낸 사람 추천 때 저장한 값을 그대로 쓴다
     * (받은 사람 → 보낸 사람 방향 추천 행은 없을 수 있다). 실패는 로그만 — 60초 후의 목록 조회가 다시 시도한다.
     */
    boolean fillRecipientReason(UUID requestId) {
        try {
            DatingRequest request = requestRepository.findWithProfiles(requestId).orElseThrow();
            if (request.getRecipientReason() != null) {
                return true;
            }
            Long senderMemberId = request.getSender().getMemberId();
            Long recipientMemberId = request.getRecipient().getMemberId();
            int score = recommendationRepository
                    .findForRequestPairs(List.of(senderMemberId), List.of(request.getRecipient().getId()))
                    .stream().findFirst().orElseThrow().getScore();
            Result recipient = resultRepository.findByMemberId(recipientMemberId).orElseThrow();
            Result sender = resultRepository.findByMemberId(senderMemberId).orElseThrow();
            String generated = generator.generate(pillars(recipient), pillars(sender), score,
                    CompatibilityTier.fromScore(score).korean());
            requestRepository.saveRecipientReasonIfAbsent(requestId, generated);
            return true;
        } catch (RuntimeException exception) {
            log.warn("dating recipient reason failed. requestId={}, cause={}", requestId,
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private static SajuPillars pillars(Result result) {
        return new SajuPillars(result.getYearPillar(), result.getMonthPillar(),
                result.getDayPillar(), result.getHourPillar());
    }
}

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    // 같은 요청의 생성이 겹치지 않게 한다. 저장은 DB 가 한 번만 받지만 LLM 호출은 그 전에 나가므로 여기서 막는다
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> senderInFlight = ConcurrentHashMap.newKeySet();

    public DatingReasonService(DatingRecommendationRepository recommendationRepository,
                               DatingRequestRepository requestRepository, ResultRepository resultRepository,
                               DatingReasonGenerator generator,
                               @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.recommendationRepository = recommendationRepository;
        this.requestRepository = requestRepository;
        this.resultRepository = resultRepository;
        this.generator = generator;
        this.taskExecutor = taskExecutor;
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
        if (!senderInFlight.add(requestId)) {
            return;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    fillSenderReason(requestId);
                } finally {
                    senderInFlight.remove(requestId);
                }
            });
        } catch (RuntimeException rejected) {
            senderInFlight.remove(requestId);
            log.warn("dating sender reason not scheduled. requestId={}, cause={}", requestId,
                    rejected.getClass().getSimpleName());
        }
    }

    void fillSenderReason(UUID requestId) {
        try {
            DatingRequest request = requestRepository.findWithProfiles(requestId).orElseThrow();
            if (request.getStatus() != DatingRequestStatus.ACCEPTED) {
                return;
            }
            Long senderMemberId = request.getSender().getMemberId();
            DatingRecommendation recommendation = recommendationRepository.findForRequestPairs(
                    List.of(senderMemberId), List.of(request.getRecipient().getId()))
                    .stream().findFirst().orElseThrow();
            if (recommendation.getReasonContent() == null) {
                generateAndSave(recommendation, senderMemberId, request.getRecipient().getMemberId());
            }
        } catch (RuntimeException exception) {
            log.warn("dating sender reason failed. requestId={}, cause={}", requestId,
                    exception.getClass().getSimpleName());
        }
    }

    /** 받은 목록 조회에서 아직 이유가 없는 요청을 다시 시도할 때도 부른다. 응답은 기다리지 않는다. */
    public void fillRecipientReasonAsync(UUID requestId) {
        if (!inFlight.add(requestId)) {
            return;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    fillRecipientReason(requestId);
                } finally {
                    inFlight.remove(requestId);
                }
            });
        } catch (RuntimeException rejected) { // 큐가 차서 거부되면 다음 목록 조회가 다시 시도할 수 있게 표시를 지운다
            inFlight.remove(requestId);
            log.warn("dating recipient reason not scheduled. requestId={}, cause={}", requestId,
                    rejected.getClass().getSimpleName());
        }
    }

    /**
     * 받은 사람(viewer)이 보낸 사람(candidate)을 보는 문장. 점수는 보낸 사람 추천 때 저장한 값을 그대로 쓴다
     * (받은 사람 → 보낸 사람 방향 추천 행은 없을 수 있다). 실패는 로그만 — 다음 목록 조회가 다시 시도한다.
     */
    void fillRecipientReason(UUID requestId) {
        try {
            DatingRequest request = requestRepository.findWithProfiles(requestId).orElseThrow();
            if (request.getRecipientReason() != null) {
                return;
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
        } catch (RuntimeException exception) {
            log.warn("dating recipient reason failed. requestId={}, cause={}", requestId,
                    exception.getClass().getSimpleName());
        }
    }

    private static SajuPillars pillars(Result result) {
        return new SajuPillars(result.getYearPillar(), result.getMonthPillar(),
                result.getDayPillar(), result.getHourPillar());
    }
}

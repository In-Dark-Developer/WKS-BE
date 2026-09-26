package com.darkness.wks.dating;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.dto.DatingRequestListResponse;
import com.darkness.wks.dating.dto.DatingRequestResponse;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.dating.entity.DatingRequest;
import com.darkness.wks.dating.entity.DatingRequestStatus;
import com.darkness.wks.member.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DatingRequestService {

    private final EntityManager entityManager;
    private final DatingProfileRepository profileRepository;
    private final DatingRecommendationRepository recommendationRepository;
    private final DatingRequestRepository requestRepository;
    private final DatingPhotoService photoService;

    public DatingRequestService(EntityManager entityManager, DatingProfileRepository profileRepository,
                                DatingRecommendationRepository recommendationRepository,
                                DatingRequestRepository requestRepository, DatingPhotoService photoService) {
        this.entityManager = entityManager;
        this.profileRepository = profileRepository;
        this.recommendationRepository = recommendationRepository;
        this.requestRepository = requestRepository;
        this.photoService = photoService;
    }

    @Transactional
    public DatingRequestResponse send(Long memberId, UUID candidateId) {
        DatingProfile candidate = profileRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
        if (candidate.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        lockMembers(memberId, candidate.getMemberId());
        DatingProfile sender = ownProfile(memberId);
        entityManager.refresh(candidate);
        if (!sender.isEligible()) {
            throw new BusinessException(ErrorCode.DATING_NOT_VERIFIED);
        }
        if (!candidate.isEligible()
                || !recommendationRepository.existsByViewerMemberIdAndCandidateIdAndActiveTrue(memberId, candidateId)
                || requestRepository.existsBetween(sender.getId(), candidateId)) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_CONFLICT);
        }
        try {
            return DatingRequestResponse.from(requestRepository.saveAndFlush(
                    new DatingRequest(sender, candidate)), memberId);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_CONFLICT);
        }
    }

    public List<DatingRequestListResponse> list(Long memberId, String box) {
        ownProfile(memberId);
        List<DatingRequest> requests = switch (box) {
            case "sent" -> requestRepository.findSent(memberId);
            case "received" -> requestRepository.findReceived(memberId, DatingRequestStatus.CANCELLED);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
        if (requests.isEmpty()) {
            return List.of();
        }
        List<Long> senderIds = requests.stream().map(request -> request.getSender().getMemberId())
                .distinct().toList();
        List<UUID> recipientIds = requests.stream().map(request -> request.getRecipient().getId())
                .distinct().toList();
        Map<RequestPair, DatingRecommendation> recommendations = recommendationRepository
                .findForRequestPairs(senderIds, recipientIds).stream()
                .collect(Collectors.toMap(
                        item -> new RequestPair(item.getViewerMemberId(), item.getCandidate().getId()),
                        Function.identity()));
        return requests.stream().map(request -> {
            DatingRecommendation recommendation = recommendations.get(new RequestPair(
                    request.getSender().getMemberId(), request.getRecipient().getId()));
            if (recommendation == null) {
                throw new IllegalStateException("Missing recommendation for dating request " + request.getId());
            }
            boolean received = request.getRecipient().getMemberId().equals(memberId);
            DatingProfile other = received ? request.getSender() : request.getRecipient();
            String originalPhotoUrl = received || recommendation.isPhotoUnlocked()
                    ? photoService.originalUrl(other.getPhoto()) : null;
            return DatingRequestListResponse.from(request, memberId, recommendation,
                    photoService.thumbnailUrl(other.getPhoto()), originalPhotoUrl);
        }).toList();
    }

    @Transactional
    public DatingRequestResponse accept(Long memberId, UUID requestId) {
        DatingRequest request = receivedRequest(memberId, requestId);
        lockMembers(request.getSender().getMemberId(), memberId);
        entityManager.refresh(request);
        if (request.getStatus() != DatingRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_CONFLICT);
        }
        DatingProfile sender = request.getSender();
        DatingProfile recipient = request.getRecipient();
        entityManager.refresh(sender);
        entityManager.refresh(recipient);
        if (!sender.isEligible() || !recipient.isEligible()) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_CONFLICT);
        }
        request.accept(Instant.now());
        return DatingRequestResponse.from(request, memberId);
    }

    @Transactional
    public DatingRequestResponse reject(Long memberId, UUID requestId) {
        DatingRequest request = receivedRequest(memberId, requestId);
        lockMembers(request.getSender().getMemberId(), memberId);
        entityManager.refresh(request);
        if (request.getStatus() != DatingRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_CONFLICT);
        }
        request.reject(Instant.now());
        return DatingRequestResponse.from(request, memberId);
    }

    @Transactional
    public DatingRequestResponse cancel(Long memberId, UUID requestId) {
        DatingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_REQUEST_NOT_FOUND));
        if (!request.getSender().getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_NOT_FOUND);
        }
        lockMembers(memberId, request.getRecipient().getMemberId());
        entityManager.refresh(request);
        if (request.getStatus() != DatingRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_CONFLICT);
        }
        request.cancel(Instant.now());
        return DatingRequestResponse.from(request, memberId);
    }

    private DatingRequest receivedRequest(Long memberId, UUID requestId) {
        DatingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_REQUEST_NOT_FOUND));
        if (!request.getRecipient().getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.DATING_REQUEST_NOT_FOUND);
        }
        return request;
    }

    private DatingProfile ownProfile(Long memberId) {
        return profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATING_PROFILE_NOT_FOUND));
    }

    private void lockMembers(Long first, Long second) {
        // 양쪽 회원 행을 같은 순서로 잠가 교차 요청·동시 수락 시 교착과 이중 성사를 막는다.
        List.of(first, second).stream().distinct().sorted(Comparator.naturalOrder()).forEach(id -> {
            if (entityManager.find(Member.class, id, LockModeType.PESSIMISTIC_WRITE) == null) {
                throw new BusinessException(ErrorCode.UNAUTHENTICATED);
            }
        });
    }

    private record RequestPair(Long senderMemberId, UUID recipientId) {
    }
}

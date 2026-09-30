package com.darkness.wks.compatibility;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.dto.CompatibilityResponse;
import com.darkness.wks.compatibility.dto.CreateCompatibilityRequest;
import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.SajuPillars;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompatibilityService {

    private final CompatibilityRepository compatibilityRepository;
    private final ResultRepository resultRepository;
    private final CompatibilityCalculator compatibilityCalculator;
    private final MapFriendRewardService mapFriendRewardService;

    /**
     * @param requesterMemberId 로그인했으면 회원 id, 아니면 {@code null}. 로그인 없이도 똑같이 동작하고, 있으면 친구 보상
     *                          판단에만 쓴다 — 주인 없는 친구 결과를 이 계정이 만든 것으로 기록한다(V27)
     */
    @Transactional
    public CreationResult createCompatibility(String shareId, CreateCompatibilityRequest request,
                                              Long requesterMemberId) {
        UUID parsedShareId = parseResultId(shareId);
        UUID guestId = parseResultId(request.guestResultId());
        Result sharedOrigin = resultRepository.findByShareId(parsedShareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        UUID originId = sharedOrigin.getId();
        if (originId.equals(guestId)) {
            throw new BusinessException(ErrorCode.SELF_COMPATIBILITY);
        }

        List<UUID> ids = List.of(originId, guestId).stream().sorted().toList();
        List<Result> results = resultRepository.findAllByIdForUpdate(ids);
        Result origin = findResult(results, originId);
        Result guest = findResult(results, guestId);

        // 로그인한 사람이 계정 대표 결과가 아닌 결과(로그인 전에 만든 것 등)로 별을 남기면 그 결과는 주인이 없다 —
        // 이 계정이 만든 것으로 기록해야 로그인 순서와 무관하게 공유자 보상이 나간다
        boolean claimed = requesterMemberId != null && guest.claimBy(requesterMemberId);

        CreationResult creation = compatibilityRepository.findByResultPair(originId, guestId)
                .map(compatibility -> new CreationResult(
                        CompatibilityResponse.from(compatibility, origin, guest),
                        false
                ))
                .orElseGet(() -> create(origin, guest));
        if (claimed) {
            // 이 결과로 전에 남긴 다른 별들도 이제 주인이 생겼다
            mapFriendRewardService.rewardAllOf(guestId);
        }
        return creation;
    }

    private CreationResult create(Result origin, Result guest) {
        int score = compatibilityCalculator.calculate(toPillars(origin), toPillars(guest));
        Compatibility compatibility = compatibilityRepository.save(new Compatibility(
                origin,
                guest,
                (short) score,
                CompatibilityTier.fromScore(score)
        ));
        // 공유자(origin)의 궁합지도에 친구가 등록됐다 — 둘 다 주인 계정이 있을 때만, 같은 계정은 한 번만(MapFriendRewardService)
        mapFriendRewardService.rewardNew(compatibility);
        return new CreationResult(CompatibilityResponse.from(compatibility, origin, guest), true);
    }

    private static Result findResult(List<Result> results, UUID id) {
        return results.stream()
                .filter(result -> result.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
    }

    private static SajuPillars toPillars(Result result) {
        return new SajuPillars(
                result.getYearPillar(),
                result.getMonthPillar(),
                result.getDayPillar(),
                result.getHourPillar()
        );
    }

    private static UUID parseResultId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value) || id.version() != 4) {
                throw new IllegalArgumentException("Invalid UUID v4");
            }
            return id;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public record CreationResult(CompatibilityResponse response, boolean created) {
    }
}

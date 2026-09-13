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

    @Transactional
    public CreationResult createCompatibility(String shareId, CreateCompatibilityRequest request) {
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

        return compatibilityRepository.findByResultPair(originId, guestId)
                .map(compatibility -> new CreationResult(
                        CompatibilityResponse.from(compatibility, origin, guest),
                        false
                ))
                .orElseGet(() -> create(origin, guest));
    }

    private CreationResult create(Result origin, Result guest) {
        int score = compatibilityCalculator.calculate(toPillars(origin), toPillars(guest));
        Compatibility compatibility = compatibilityRepository.save(new Compatibility(
                origin,
                guest,
                (short) score,
                CompatibilityTier.fromScore(score)
        ));
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

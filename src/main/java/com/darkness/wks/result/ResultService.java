package com.darkness.wks.result;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.CompatibilityRepository;
import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.result.dto.CreateResultRequest;
import com.darkness.wks.result.dto.ResultInputResponse;
import com.darkness.wks.result.dto.ResultResponse;
import com.darkness.wks.result.dto.UpdateNicknameRequest;
import com.darkness.wks.result.dto.SharedResultResponse;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.BirthDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import com.darkness.wks.saju.SajuPillars;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultService {

    private final ResultRepository resultRepository;
    private final ReadingRepository readingRepository;
    private final CompatibilityRepository compatibilityRepository;
    private final ResultAnalysisPort resultAnalysisPort;

    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1950, 1, 1);

    /**
     * @param memberId 로그인했으면 회원 id, 아니면 {@code null}. 계정에 결과가 없을 때만 새 결과를 계정에
     *                 연결한다 — 이미 있으면 계정 결과를 그대로 두고 새 결과는 익명으로 남긴다(plan.md §1.1 과
     *                 같은 "계정 우선", TBD-14 중 로그인 후 결과 생성 부분, 2026-09-26)
     */
    @Transactional
    public ResultResponse createResult(CreateResultRequest request, Long memberId) {
        LocalDate birthDate = toSolar(request);
        int version = resultAnalysisPort.analysisVersion();
        // 같은 입력·같은 버전이면 저장된 해석을 복사한다. Gemini 호출 없음 (#62)
        ResultAnalysisPort.AnalysisResult analysis = readingRepository
                .findReusable(birthDate, request.birthTime(), request.gender(), version)
                .map(ResultService::toAnalysis)
                .orElseGet(() -> resultAnalysisPort.analyze(birthDate, request.birthTime(), request.gender()));

        Result result = new Result(
                request.nickname(),
                birthDate,
                request.birthTime(),
                null, // 출생 지역은 받지 않는다 (api-spec §2, 2026-09-13)
                request.gender(),
                analysis.pillars().yearPillar(),
                analysis.pillars().monthPillar(),
                analysis.pillars().dayPillar(),
                analysis.pillars().hourPillar(),
                request.calendarType(),
                request.birthDate(), // 입력 원본. 폼 자동 채움용 (#66)
                request.leapMonth()
        );
        // 잠금은 해석(LLM 호출 가능) 뒤에 잡는다 — 잠근 채로 최대 수십 초를 기다리지 않게
        if (memberId != null && resultRepository.lockMember(memberId).isPresent()
                && !resultRepository.existsByMemberId(memberId)) {
            result.linkMember(memberId);
        }
        result = resultRepository.save(result);

        ResultAnalysisPort.Fortune marriage = analysis.fortune(FortuneCategory.MARRIAGE);
        ResultAnalysisPort.Fortune children = analysis.fortune(FortuneCategory.CHILDREN);
        ResultAnalysisPort.Fortune love = analysis.fortune(FortuneCategory.LOVE);
        Reading reading = new Reading(
                result,
                analysis.destinyDescription(),
                marriage.score(),
                marriage.content(),
                children.score(),
                children.content(),
                love.score(),
                love.content(),
                analysis.elementMatchReason(),
                version
        );
        readingRepository.save(reading);

        return ResultResponse.from(result, reading);
    }

    private static ResultAnalysisPort.AnalysisResult toAnalysis(Reading r) {
        Result src = r.getResult();
        return new ResultAnalysisPort.AnalysisResult(
                new SajuPillars(src.getYearPillar(), src.getMonthPillar(), src.getDayPillar(), src.getHourPillar()),
                r.getDestinyContent(),
                List.of(
                        new ResultAnalysisPort.Fortune(FortuneCategory.MARRIAGE, r.getMarriageScore(), r.getMarriageContent()),
                        new ResultAnalysisPort.Fortune(FortuneCategory.CHILDREN, r.getChildrenScore(), r.getChildrenContent()),
                        new ResultAnalysisPort.Fortune(FortuneCategory.LOVE, r.getLoveScore(), r.getLoveContent())
                ),
                r.getElementMatchContent()
        );
    }

    /** 입력 폼 자동 채움용. 저장된 입력값을 그대로 돌려준다 (#66) */
    public ResultInputResponse getResultInput(String resultId) {
        Result result = resultRepository.findById(parseResultId(resultId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        return ResultInputResponse.from(result);
    }

    /** 닉네임만 변경한다 (#68). 해석·점수·궁합은 그대로 */
    @Transactional
    public ResultResponse updateNickname(String resultId, UpdateNicknameRequest request) {
        Result result = resultRepository.findById(parseResultId(resultId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        result.rename(request.nickname());
        return getResultResponse(result);
    }

    public ResultResponse getResult(String resultId) {
        UUID id = parseResultId(resultId);
        Result result = resultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        return getResultResponse(result);
    }

    public SharedResultResponse getSharedResult(String shareId) {
        UUID id = parseResultId(shareId);
        Result result = resultRepository.findByShareId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        return SharedResultResponse.from(getResultResponse(result));
    }

    private ResultResponse getResultResponse(Result result) {
        UUID id = result.getId();
        Reading reading = readingRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Reading not found for result: " + id));
        List<Compatibility> compatibilities = compatibilityRepository.findAllByResultIdOrderByCreatedAtDesc(id);

        return ResultResponse.from(result, reading, compatibilities);
    }

    /** 음력이면 양력으로 변환. 없는 날짜·윤달, 1950-01-01 ~ 오늘 범위 밖이면 INVALID_INPUT */
    private static LocalDate toSolar(CreateResultRequest request) {
        LocalDate solar;
        try {
            solar = BirthDate.parse(request.calendarType(), request.birthDate(), request.leapMonth()).toSolar();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (solar.isBefore(MIN_BIRTH_DATE) || solar.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return solar;
    }

    private UUID parseResultId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value) || id.version() != 4) {
                throw new IllegalArgumentException("Invalid UUID v4");
            }
            return id;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}

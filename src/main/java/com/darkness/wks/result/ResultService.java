package com.darkness.wks.result;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.result.dto.CreateResultRequest;
import com.darkness.wks.result.dto.ResultResponse;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.BirthDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultService {

    private final ResultRepository resultRepository;
    private final ReadingRepository readingRepository;
    private final ResultAnalysisPort resultAnalysisPort;

    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1950, 1, 1);

    @Transactional
    public ResultResponse createResult(CreateResultRequest request) {
        LocalDate birthDate = toSolar(request);
        ResultAnalysisPort.AnalysisResult analysis = resultAnalysisPort.analyze(birthDate, request.birthTime());

        Result result = resultRepository.save(new Result(
                request.nickname(),
                birthDate,
                request.birthTime(),
                null, // 출생 지역은 받지 않는다 (api-spec §2, 2026-09-13)
                request.gender(),
                analysis.pillars().yearPillar(),
                analysis.pillars().monthPillar(),
                analysis.pillars().dayPillar(),
                analysis.pillars().hourPillar()
        ));

        ResultAnalysisPort.Fortune marriage = analysis.fortune(FortuneCategory.MARRIAGE);
        ResultAnalysisPort.Fortune children = analysis.fortune(FortuneCategory.CHILDREN);
        ResultAnalysisPort.Fortune love = analysis.fortune(FortuneCategory.LOVE);
        Reading reading = new Reading(
                result,
                analysis.destiny().title(),
                analysis.destiny().description(),
                marriage.grade(),
                marriage.content(),
                children.grade(),
                children.content(),
                love.grade(),
                love.content(),
                analysis.luckyItem(),
                analysis.luckyPlace()
        );
        readingRepository.save(reading);

        return ResultResponse.from(result, reading);
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
}

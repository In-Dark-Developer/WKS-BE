package com.darkness.wks.result;

import com.darkness.wks.result.dto.CreateResultRequest;
import com.darkness.wks.result.dto.ResultResponse;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultService {

    private final ResultRepository resultRepository;
    private final ReadingRepository readingRepository;
    private final ResultAnalysisPort resultAnalysisPort;

    @Transactional
    public ResultResponse createResult(CreateResultRequest request) {
        ResultAnalysisPort.AnalysisResult analysis = resultAnalysisPort.analyze(
                request.birthDate(),
                request.birthTime(),
                request.birthRegion()
        );

        Result result = resultRepository.save(new Result(
                request.nickname(),
                request.birthDate(),
                request.birthTime(),
                request.birthRegion(),
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
}

package com.darkness.wks.result;

import com.darkness.wks.result.dto.ResultResponse;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사주 결과 저장만 짧은 트랜잭션으로 묶는다. {@link ResultService#createResult} 는 Gemini 호출 동안 DB 커넥션을
 * 쥐지 않으려고 트랜잭션 없이 돌고, 해석이 끝난 뒤 여기서 저장한다. 같은 클래스 안에서 부르면(self-invocation)
 * 스프링 프록시를 거치지 않아 트랜잭션이 시작되지 않으므로 별도 빈으로 뺐다 (DatingUnlockChargeService 와 같은 이유).
 */
@Component
class ResultSaver {

    private final ResultRepository resultRepository;
    private final ReadingRepository readingRepository;

    ResultSaver(ResultRepository resultRepository, ReadingRepository readingRepository) {
        this.resultRepository = resultRepository;
        this.readingRepository = readingRepository;
    }

    /**
     * @param memberId 로그인했으면 회원 id. 계정에 결과가 없을 때만 새 결과를 연결한다(계정 우선, TBD-14 중 결과 생성 부분).
     *                 회원 행 잠금은 해석이 끝난 이 시점에만 잡는다 — 잠근 채로 LLM 을 기다리지 않게
     */
    @Transactional
    ResultResponse save(Result result, ResultAnalysisPort.AnalysisResult analysis, int version, Long memberId) {
        if (memberId != null && resultRepository.lockMember(memberId).isPresent()
                && !resultRepository.existsByMemberId(memberId)) {
            result.linkMember(memberId);
        }
        Result saved = resultRepository.save(result);

        ResultAnalysisPort.Fortune marriage = analysis.fortune(FortuneCategory.MARRIAGE);
        ResultAnalysisPort.Fortune children = analysis.fortune(FortuneCategory.CHILDREN);
        ResultAnalysisPort.Fortune love = analysis.fortune(FortuneCategory.LOVE);
        Reading reading = new Reading(
                saved,
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

        return ResultResponse.from(saved, reading);
    }
}

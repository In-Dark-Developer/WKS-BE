package com.darkness.wks.result;

import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.CompatibilityRepository;
import com.darkness.wks.result.dto.CreateResultRequest;
import com.darkness.wks.result.dto.ResultResponse;
import com.darkness.wks.saju.CalendarType;
import org.mockito.ArgumentCaptor;
import com.darkness.wks.result.dto.SharedResultResponse;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.SajuPillars;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceTest {

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private ReadingRepository readingRepository;

    @Mock
    private CompatibilityRepository compatibilityRepository;

    @Mock
    private ResultAnalysisPort resultAnalysisPort;

    @InjectMocks
    private ResultService resultService;

    @Test
    void getsStoredResultWithoutCallingAnalysisModule() {
        UUID resultId = UUID.randomUUID();
        Result result = result(resultId);
        Reading reading = reading(result);
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(result));
        when(readingRepository.findById(resultId)).thenReturn(Optional.of(reading));
        when(compatibilityRepository.findAllByResultIdOrderByCreatedAtDesc(resultId)).thenReturn(List.of());

        ResultResponse response = resultService.getResult(resultId.toString());

        assertThat(response.resultId()).isEqualTo(resultId);
        assertThat(response.shareId()).isEqualTo(result.getShareId());
        assertThat(response.shareId().version()).isEqualTo(4);
        assertThat(response.nickname()).isEqualTo("도윤");
        assertThat(response.fortunes()).extracting(ResultResponse.FortuneResponse::grade)
                .containsExactly("SS", "A+", "B");
        assertThat(response.elements()).isEqualTo(new ResultResponse.ElementResponse(2, 1, 1, 0, 2));
        assertThat(response.compatibilities()).isEmpty();
        verifyNoInteractions(resultAnalysisPort);
    }

    @Test
    void countsEightElementsWhenHourPillarExists() {
        ResultResponse.ElementResponse elements = ResultResponse.ElementResponse.from(
                new SajuPillars("임오", "계묘", "갑진", "경신")
        );

        assertThat(elements).isEqualTo(new ResultResponse.ElementResponse(2, 1, 1, 2, 2));
    }

    @Test
    void throwsResultNotFoundWhenResultDoesNotExist() {
        UUID resultId = UUID.randomUUID();
        when(resultRepository.findById(resultId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resultService.getResult(resultId.toString()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESULT_NOT_FOUND));
    }

    @Test
    void getsPublicResultByShareIdWithoutExposingIdentifiers() {
        UUID resultId = UUID.randomUUID();
        Result result = result(resultId);
        Reading reading = reading(result);
        when(resultRepository.findByShareId(result.getShareId())).thenReturn(Optional.of(result));
        when(readingRepository.findById(resultId)).thenReturn(Optional.of(reading));
        when(compatibilityRepository.findAllByResultIdOrderByCreatedAtDesc(resultId)).thenReturn(List.of());

        SharedResultResponse response = resultService.getSharedResult(result.getShareId().toString());

        assertThat(response.nickname()).isEqualTo("도윤");
        assertThat(SharedResultResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("resultId", "shareId");
    }

    @Test
    void rejectsMalformedResultId() {
        assertThatThrownBy(() -> resultService.getResult("not-a-uuid"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verifyNoInteractions(resultRepository, readingRepository, compatibilityRepository, resultAnalysisPort);
    }

    @Test
    void rejectsUuidThatIsNotVersionFour() {
        String versionOneUuid = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

        assertThatThrownBy(() -> resultService.getResult(versionOneUuid))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verifyNoInteractions(resultRepository, readingRepository, compatibilityRepository, resultAnalysisPort);
    }

    private Result result(UUID id) {
        Result result = new Result(
                "도윤",
                LocalDate.of(2002, 3, 14),
                null,
                "서울",
                Gender.MALE,
                "임오",
                "계묘",
                "갑진",
                null
        );
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private Reading reading(Result result) {
        return new Reading(
                result,
                "운명 설명",
                95, // SS (컷 94)
                "결혼운 설명",
                75, // A+ (컷 74)
                "자녀운 설명",
                30, // B
                "연애운 설명",
                7
        );
    }

    @Test
    void returnsStoredInputForFormPrefill() {
        UUID resultId = UUID.randomUUID();
        Result lunar = new Result("도윤", LocalDate.of(2002, 4, 26), java.time.LocalTime.of(14, 30), null, Gender.FEMALE,
                "임오", "계묘", "갑진", "신미", CalendarType.LUNAR, "2002-03-14", true);
        ReflectionTestUtils.setField(lunar, "id", resultId);
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(lunar));

        var input = resultService.getResultInput(resultId.toString());

        assertThat(input.calendarType()).isEqualTo(CalendarType.LUNAR);
        assertThat(input.birthDate()).isEqualTo("2002-03-14"); // 양력 변환값(4/26)이 아니라 입력 원본
        assertThat(input.isLeapMonth()).isTrue();
        assertThat(input.birthTime()).isEqualTo(java.time.LocalTime.of(14, 30));
        assertThat(input.gender()).isEqualTo(Gender.FEMALE);
        assertThat(input.nickname()).isEqualTo("도윤");
    }

    @Test
    void throwsResultNotFoundWhenInputDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(resultRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resultService.getResultInput(missing.toString()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    @Test
    void renamesNicknameKeepingEverythingElse() {
        UUID resultId = UUID.randomUUID();
        Result result = result(resultId);
        UUID shareId = result.getShareId();
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(result));
        when(readingRepository.findById(resultId)).thenReturn(Optional.of(reading(result)));
        when(compatibilityRepository.findAllByResultIdOrderByCreatedAtDesc(resultId)).thenReturn(List.of());

        ResultResponse response = resultService.updateNickname(resultId.toString(),
                new com.darkness.wks.result.dto.UpdateNicknameRequest("민수"));

        assertThat(response.nickname()).isEqualTo("민수");
        assertThat(result.getNickname()).isEqualTo("민수");
        assertThat(response.resultId()).isEqualTo(resultId);
        assertThat(response.shareId()).isEqualTo(shareId); // 링크 유지
        assertThat(response.fortunes()).extracting(ResultResponse.FortuneResponse::grade)
                .containsExactly("SS", "A+", "B"); // 점수·해석 그대로
        verifyNoInteractions(resultAnalysisPort);
    }

    @Test
    void throwsResultNotFoundWhenRenamingMissingResult() {
        UUID missing = UUID.randomUUID();
        when(resultRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resultService.updateNickname(missing.toString(),
                new com.darkness.wks.result.dto.UpdateNicknameRequest("민수")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESULT_NOT_FOUND);
    }

    private static CreateResultRequest request(String nickname) {
        return new CreateResultRequest(nickname, CalendarType.SOLAR, "2002-03-14", null, null, Gender.MALE);
    }

    @Test
    void reusesStoredReadingForSameInputWithoutAnalysis() {
        Result stored = result(UUID.randomUUID());
        when(resultAnalysisPort.analysisVersion()).thenReturn(7);
        when(readingRepository.findReusable(LocalDate.of(2002, 3, 14), null, Gender.MALE, 7)).thenReturn(Optional.of(reading(stored)));
        when(resultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(readingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultResponse response = resultService.createResult(request("민수"));

        verify(resultAnalysisPort, never()).analyze(any(), any(), any());
        assertThat(response.nickname()).isEqualTo("민수"); // 닉네임은 새 result 것, 문장·점수는 복사
        assertThat(response.fortunes()).extracting(ResultResponse.FortuneResponse::grade).containsExactly("SS", "A+", "B");
        assertThat(response.fortunes()).extracting(ResultResponse.FortuneResponse::content).containsExactly("결혼운 설명", "자녀운 설명", "연애운 설명");
        assertThat(response.destiny().description()).isEqualTo("운명 설명");
    }

    @Test
    void analysesWhenNoReusableReadingAndStoresVersion() {
        when(resultAnalysisPort.analysisVersion()).thenReturn(7);
        when(readingRepository.findReusable(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(resultAnalysisPort.analyze(LocalDate.of(2002, 3, 14), null, Gender.MALE)).thenReturn(new ResultAnalysisPort.AnalysisResult(
                new SajuPillars("임오", "계묘", "갑진", null), "설명",
                List.of(new ResultAnalysisPort.Fortune(FortuneCategory.MARRIAGE, 95, "a"),
                        new ResultAnalysisPort.Fortune(FortuneCategory.CHILDREN, 75, "b"),
                        new ResultAnalysisPort.Fortune(FortuneCategory.LOVE, 30, "c"))));
        when(resultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(readingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        resultService.createResult(request("도윤"));

        ArgumentCaptor<Reading> saved = ArgumentCaptor.forClass(Reading.class);
        verify(readingRepository).save(saved.capture());
        assertThat(saved.getValue().getVersion()).isEqualTo(7);
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        verify(resultRepository).save(savedResult.capture());
        assertThat(savedResult.getValue().getCalendarType()).isEqualTo(CalendarType.SOLAR);
        assertThat(savedResult.getValue().getBirthDateInput()).isEqualTo("2002-03-14");
        verify(resultAnalysisPort).analyze(any(), any(), any());
    }
}

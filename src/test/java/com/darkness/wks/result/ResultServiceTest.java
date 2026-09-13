package com.darkness.wks.result;

import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.CompatibilityRepository;
import com.darkness.wks.result.dto.ResultResponse;
import com.darkness.wks.result.entity.Reading;
import com.darkness.wks.result.entity.Result;
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
        assertThat(response.nickname()).isEqualTo("도윤");
        assertThat(response.fortunes()).extracting(ResultResponse.FortuneResponse::grade)
                .containsExactly("SS", "A+", "B");
        assertThat(response.compatibilities()).isEmpty();
        verifyNoInteractions(resultAnalysisPort);
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
                "운명 제목",
                "운명 설명",
                90,
                "결혼운 설명",
                70,
                "자녀운 설명",
                30,
                "연애운 설명",
                "파란색 팔찌",
                "야외 무대"
        );
    }
}

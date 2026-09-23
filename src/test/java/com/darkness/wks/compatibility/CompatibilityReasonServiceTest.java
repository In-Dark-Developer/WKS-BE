package com.darkness.wks.compatibility;

import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.dto.CompatibilityReasonResponse;
import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.saju.CompatibilityReason;
import com.darkness.wks.saju.CompatibilityReasonGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Gemini 는 호출하지 않는다 (TR-E-01). 캐시 적중·생성·동시 생성 경합만 검증 */
@ExtendWith(MockitoExtension.class)
class CompatibilityReasonServiceTest {

    private static final CompatibilityReason REASON = new CompatibilityReason("왜", "만나면", "싸우면");

    @Mock
    private CompatibilityRepository compatibilityRepository;

    @Mock
    private CompatibilityReasonGenerator generator;

    @InjectMocks
    private CompatibilityReasonService service;

    @Test
    void cachedReasonSkipsLlm() {
        Compatibility c = compatibility(7L);
        ReflectionTestUtils.setField(c, "reasonWhy", "저장된 왜");
        ReflectionTestUtils.setField(c, "reasonTogether", "저장된 만나면");
        ReflectionTestUtils.setField(c, "reasonConflict", "저장된 싸우면");
        when(compatibilityRepository.findByIdWithResults(7L)).thenReturn(Optional.of(c));

        CompatibilityReasonResponse r = service.getReason(7L);

        assertThat(r).isEqualTo(new CompatibilityReasonResponse("저장된 왜", "저장된 만나면", "저장된 싸우면"));
        verifyNoInteractions(generator);
        verify(compatibilityRepository, never()).saveReasonIfAbsent(any(), any(), any(), any());
    }

    @Test
    void generatesOnceAndSavesWhenAbsent() {
        Compatibility c = compatibility(7L);
        when(compatibilityRepository.findByIdWithResults(7L)).thenReturn(Optional.of(c));
        when(generator.generate(any(), any(), anyInt(), anyString())).thenReturn(REASON);
        when(compatibilityRepository.saveReasonIfAbsent(7L, "왜", "만나면", "싸우면")).thenReturn(1);

        CompatibilityReasonResponse r = service.getReason(7L);

        assertThat(r).isEqualTo(new CompatibilityReasonResponse("왜", "만나면", "싸우면"));
        verify(generator).generate(any(), any(), eq(92), eq("귀인"));
    }

    @Test
    void loserOfConcurrentGenerationReturnsStoredReason() {
        Compatibility c = compatibility(7L);
        Compatibility stored = compatibility(7L);
        ReflectionTestUtils.setField(stored, "reasonWhy", "먼저 저장된 왜");
        ReflectionTestUtils.setField(stored, "reasonTogether", "먼저");
        ReflectionTestUtils.setField(stored, "reasonConflict", "먼저");
        when(compatibilityRepository.findByIdWithResults(7L)).thenReturn(Optional.of(c)).thenReturn(Optional.of(stored));
        when(generator.generate(any(), any(), anyInt(), anyString())).thenReturn(REASON);
        when(compatibilityRepository.saveReasonIfAbsent(7L, "왜", "만나면", "싸우면")).thenReturn(0);

        CompatibilityReasonResponse r = service.getReason(7L);

        assertThat(r.why()).isEqualTo("먼저 저장된 왜"); // 두 사람이 같은 글을 본다
    }

    @Test
    void missingCompatibilityIs404() {
        when(compatibilityRepository.findByIdWithResults(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReason(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COMPATIBILITY_NOT_FOUND);
        verifyNoInteractions(generator);
    }

    private static Compatibility compatibility(Long id) {
        Result origin = new Result("서연", LocalDate.of(2002, 3, 14), null, null, Gender.MALE, "임오", "계묘", "갑진", "신미");
        Result guest = new Result("민수", LocalDate.of(2001, 1, 1), null, null, Gender.FEMALE, "정축", "을해", "기유", null);
        Compatibility c = new Compatibility(origin, guest, (short) 92, CompatibilityTier.GUIIN);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}

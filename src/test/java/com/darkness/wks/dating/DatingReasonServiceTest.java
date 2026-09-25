package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatingReasonServiceTest {

    @Mock
    DatingRecommendationRepository recommendationRepository;

    @Mock
    ResultRepository resultRepository;

    @Mock
    DatingReasonGenerator generator;

    @InjectMocks
    DatingReasonService service;

    @Test
    void cachedReasonSkipsGeneration() {
        DatingRecommendation recommendation = recommendation();
        ReflectionTestUtils.setField(recommendation, "reasonContent", "저장된 이유");
        when(recommendationRepository.findActiveWithCandidate(1L, recommendation.getCandidate().getId()))
                .thenReturn(Optional.of(recommendation));

        assertThat(service.getOrCreate(1L, recommendation.getCandidate().getId())).isEqualTo("저장된 이유");
        verifyNoInteractions(resultRepository, generator);
    }

    @Test
    void generatesAndStoresOnFirstRequest() {
        DatingRecommendation recommendation = recommendation();
        UUID candidateId = recommendation.getCandidate().getId();
        when(recommendationRepository.findActiveWithCandidate(1L, candidateId))
                .thenReturn(Optional.of(recommendation));
        when(resultRepository.findByMemberId(1L)).thenReturn(Optional.of(result(Gender.MALE)));
        when(resultRepository.findByMemberId(2L)).thenReturn(Optional.of(result(Gender.FEMALE)));
        when(generator.generate(any(), any(), eq(82), eq("찰떡"))).thenReturn("새 이유");
        when(recommendationRepository.saveReasonIfAbsent(7L, "새 이유")).thenReturn(1);

        assertThat(service.getOrCreate(1L, candidateId)).isEqualTo("새 이유");
        verify(generator).generate(any(), any(), eq(82), eq("찰떡"));
    }

    @Test
    void concurrentWriterReturnsStoredReason() {
        DatingRecommendation recommendation = recommendation();
        UUID candidateId = recommendation.getCandidate().getId();
        when(recommendationRepository.findActiveWithCandidate(1L, candidateId))
                .thenReturn(Optional.of(recommendation));
        when(resultRepository.findByMemberId(1L)).thenReturn(Optional.of(result(Gender.MALE)));
        when(resultRepository.findByMemberId(2L)).thenReturn(Optional.of(result(Gender.FEMALE)));
        when(generator.generate(any(), any(), eq(82), eq("찰떡"))).thenReturn("늦은 이유");
        when(recommendationRepository.findReasonContentById(7L)).thenReturn(Optional.of("먼저 저장된 이유"));

        assertThat(service.getOrCreate(1L, candidateId)).isEqualTo("먼저 저장된 이유");
    }

    @Test
    void unlistedCandidateCannotTriggerGeneration() {
        UUID candidateId = UUID.randomUUID();
        when(recommendationRepository.findActiveWithCandidate(1L, candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreate(1L, candidateId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DATING_PROFILE_NOT_FOUND);
        verifyNoInteractions(resultRepository, generator);
    }

    private static DatingRecommendation recommendation() {
        DatingProfile candidate = new DatingProfile(2L, "candidate@example.com", "상대", ContactMethod.PHONE,
                "01012345678", "학과", "INTP", "소개", org.mockito.Mockito.mock(DatingPhoto.class));
        DatingRecommendation recommendation = new DatingRecommendation(1L, candidate, 82);
        ReflectionTestUtils.setField(recommendation, "id", 7L);
        return recommendation;
    }

    private static Result result(Gender gender) {
        return new Result("테스트", LocalDate.of(2002, 1, 1), null, null, gender,
                "갑자", "을축", "병인", null);
    }
}

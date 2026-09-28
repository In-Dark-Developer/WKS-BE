package com.darkness.wks.compatibility;

import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.compatibility.dto.CreateCompatibilityRequest;
import com.darkness.wks.compatibility.entity.Compatibility;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.wallet.LedgerReason;
import com.darkness.wks.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompatibilityServiceTest {

    @Mock
    private CompatibilityRepository compatibilityRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private CompatibilityCalculator compatibilityCalculator;

    @Mock
    private WalletService walletService;

    private CompatibilityService compatibilityService;

    @BeforeEach
    void setUp() {
        // 지급 규칙은 MapFriendRewardService 로 옮겼다 — 실제 객체를 붙여 원장 호출을 그대로 검증한다
        compatibilityService = new CompatibilityService(compatibilityRepository, resultRepository,
                compatibilityCalculator, new MapFriendRewardService(compatibilityRepository, walletService));
    }

    @Test
    void createsAndStoresNewCompatibility() {
        Result origin = result("서연", UUID.randomUUID(), "임오", "계묘", "갑진", "신미");
        Result guest = result("민수", UUID.randomUUID(), "정축", "을해", "기유", "병자");
        when(resultRepository.findByShareId(origin.getShareId())).thenReturn(Optional.of(origin));
        when(resultRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(origin, guest));
        when(compatibilityRepository.findByResultPair(origin.getId(), guest.getId())).thenReturn(Optional.empty());
        when(compatibilityCalculator.calculate(any(), any())).thenReturn(92);
        when(compatibilityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompatibilityService.CreationResult result = compatibilityService.createCompatibility(
                origin.getShareId().toString(),
                new CreateCompatibilityRequest(guest.getId().toString())
        );

        assertThat(result.created()).isTrue();
        assertThat(result.response().score()).isEqualTo(92);
        assertThat(result.response().tier()).isEqualTo(CompatibilityTier.GUIIN);
        assertThat(result.response().originNickname()).isEqualTo("서연");
        assertThat(result.response().guestNickname()).isEqualTo("민수");

        ArgumentCaptor<Compatibility> saved = ArgumentCaptor.forClass(Compatibility.class);
        verify(compatibilityRepository).save(saved.capture());
        assertThat(saved.getValue().getOrigin()).isSameAs(origin);
        assertThat(saved.getValue().getGuest()).isSameAs(guest);
        verifyNoInteractions(walletService); // origin이 비로그인(memberId 없음)이라 지급 없음
    }

    @Test
    void 공유자가_로그인_계정이면_친구_등록_실을_지급한다() {
        Result origin = result("서연", UUID.randomUUID(), "임오", "계묘", "갑진", "신미");
        ReflectionTestUtils.setField(origin, "memberId", 42L);
        Result guest = result("민수", UUID.randomUUID(), "정축", "을해", "기유", "병자");
        when(resultRepository.findByShareId(origin.getShareId())).thenReturn(Optional.of(origin));
        when(resultRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(origin, guest));
        when(compatibilityRepository.findByResultPair(origin.getId(), guest.getId())).thenReturn(Optional.empty());
        when(compatibilityCalculator.calculate(any(), any())).thenReturn(92);
        when(compatibilityRepository.save(any())).thenAnswer(invocation -> {
            Compatibility compatibility = invocation.getArgument(0);
            ReflectionTestUtils.setField(compatibility, "id", 7L);
            return compatibility;
        });

        compatibilityService.createCompatibility(
                origin.getShareId().toString(), new CreateCompatibilityRequest(guest.getId().toString()));

        // ref_id 는 궁합 id 가 아니라 친구의 팔자·성별 해시 — 같은 사람이 결과를 새로 만들어 다시 등록해도 한 번만
        // 친구 5명마다 3실 — 몇 번째인지는 원장이 센다
        verify(walletService).creditEveryNth(42L, LedgerReason.MAP_FRIEND, MapFriendRewardService.personKey(guest), 5, 3);
    }

    @Test
    void 비로그인_공유자는_실을_지급하지_않는다() {
        Result origin = result("서연", UUID.randomUUID(), "임오", "계묘", "갑진", "신미");
        Result guest = result("민수", UUID.randomUUID(), "정축", "을해", "기유", "병자");
        when(resultRepository.findByShareId(origin.getShareId())).thenReturn(Optional.of(origin));
        when(resultRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(origin, guest));
        when(compatibilityRepository.findByResultPair(origin.getId(), guest.getId())).thenReturn(Optional.empty());
        when(compatibilityCalculator.calculate(any(), any())).thenReturn(92);
        when(compatibilityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        compatibilityService.createCompatibility(
                origin.getShareId().toString(), new CreateCompatibilityRequest(guest.getId().toString()));

        verifyNoInteractions(walletService);
    }

    @Test
    void returnsExistingReversePairWithoutRecalculating() {
        Result origin = result("서연", UUID.randomUUID(), "임오", "계묘", "갑진", null);
        Result guest = result("민수", UUID.randomUUID(), "정축", "을해", "기유", null);
        Compatibility existing = new Compatibility(guest, origin, (short) 82, CompatibilityTier.CHALTTEOK);
        when(resultRepository.findByShareId(origin.getShareId())).thenReturn(Optional.of(origin));
        when(resultRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(origin, guest));
        when(compatibilityRepository.findByResultPair(origin.getId(), guest.getId())).thenReturn(Optional.of(existing));

        CompatibilityService.CreationResult result = compatibilityService.createCompatibility(
                origin.getShareId().toString(),
                new CreateCompatibilityRequest(guest.getId().toString())
        );

        assertThat(result.created()).isFalse();
        assertThat(result.response().score()).isEqualTo(82);
        assertThat(result.response().originNickname()).isEqualTo("서연");
        assertThat(result.response().guestNickname()).isEqualTo("민수");
        verifyNoInteractions(compatibilityCalculator);
        verify(compatibilityRepository, never()).save(any());
    }

    @Test
    void rejectsSelfCompatibilityBeforeDatabaseAccess() {
        Result origin = result("서연", UUID.randomUUID(), "임오", "계묘", "갑진", null);
        when(resultRepository.findByShareId(origin.getShareId())).thenReturn(Optional.of(origin));

        assertThatThrownBy(() -> compatibilityService.createCompatibility(
                origin.getShareId().toString(),
                new CreateCompatibilityRequest(origin.getId().toString())
        )).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SELF_COMPATIBILITY));

        verifyNoInteractions(compatibilityRepository, compatibilityCalculator);
    }

    @Test
    void rejectsMissingResult() {
        UUID shareId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        when(resultRepository.findByShareId(shareId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> compatibilityService.createCompatibility(
                shareId.toString(),
                new CreateCompatibilityRequest(guestId.toString())
        )).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESULT_NOT_FOUND));

        verifyNoInteractions(compatibilityCalculator);
    }

    @Test
    void rejectsMalformedResultId() {
        assertThatThrownBy(() -> compatibilityService.createCompatibility(
                "not-a-uuid",
                new CreateCompatibilityRequest(UUID.randomUUID().toString())
        )).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verifyNoInteractions(resultRepository, compatibilityRepository, compatibilityCalculator);
    }

    private Result result(String nickname, UUID id, String year, String month, String day, String hour) {
        Result result = new Result(
                nickname,
                LocalDate.of(2002, 3, 14),
                null,
                null,
                Gender.MALE,
                year,
                month,
                day,
                hour
        );
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }
}

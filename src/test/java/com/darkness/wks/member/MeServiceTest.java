package com.darkness.wks.member;

import com.darkness.wks.dating.DatingProfileRepository;
import com.darkness.wks.member.dto.MeResponse;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.ResultService;
import com.darkness.wks.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    private static final long MEMBER_ID = 12L;

    @Mock
    private ResultRepository resultRepository;
    @Mock
    private ResultService resultService;
    @Mock
    private WalletService walletService;
    @Mock
    private DatingProfileRepository datingProfileRepository;

    @InjectMocks
    private MeService meService;

    @Test
    void 소개팅_프로필이_있으면_hasDatingProfile_이_true_다() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(datingProfileRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(walletService.getBalance(MEMBER_ID)).thenReturn(7);

        MeResponse response = meService.getMe(MEMBER_ID);

        assertThat(response).isEqualTo(new MeResponse(MEMBER_ID, true, true, 7));
    }

    @Test
    void 소개팅_프로필이_없으면_hasDatingProfile_이_false_다() {
        when(resultRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(datingProfileRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);
        when(walletService.getBalance(MEMBER_ID)).thenReturn(0);

        MeResponse response = meService.getMe(MEMBER_ID);

        assertThat(response.hasDatingProfile()).isFalse();
    }
}

package com.darkness.wks.wallet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerRewardServiceTest {

    private final WalletService walletService = mock(WalletService.class);

    @Test
    void grantsRegisteredCodeOnceWithCaseInsensitiveRef() {
        when(walletService.credit(1L, LedgerReason.PARTNER, "FESTIVAL", 10)).thenReturn(true, false);
        PartnerRewardService service = new PartnerRewardService(walletService, "FESTIVAL:10:동국대 축제");

        assertThat(service.grant(1L, " festival ")).contains(new PartnerRewardService.Granted("동국대 축제", 10));
        // 두 번째는 원장에 이미 있어 지급되지 않는다
        assertThat(service.grant(1L, "FESTIVAL")).isEmpty();
    }

    @Test
    void ignoresUnknownOrBlankRefWithoutTouchingLedger() {
        PartnerRewardService service = new PartnerRewardService(walletService, "FESTIVAL:10:동국대 축제");

        assertThat(service.grant(1L, "OTHER")).isEmpty();
        assertThat(service.grant(1L, null)).isEmpty();
        assertThat(service.grant(1L, " ")).isEmpty();
        verify(walletService, never()).credit(any(), any(), anyString(), anyInt());
    }

    /** .env 오타로 앱이 안 뜨면 안 된다 — 틀린 항목만 건너뛴다 */
    @Test
    void skipsMalformedEntries() {
        assertThat(PartnerRewardService.parse("FESTIVAL:10:동국대 축제, BROKEN, BAD:abc:이름, ZERO:0:이름, CLUB:5:동아리"))
                .containsOnlyKeys("FESTIVAL", "CLUB");
        assertThat(PartnerRewardService.parse("")).isEmpty();
    }
}

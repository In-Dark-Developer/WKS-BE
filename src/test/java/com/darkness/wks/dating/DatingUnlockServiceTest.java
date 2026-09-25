package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.dating.dto.DatingUnlockResponse;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatingUnlockServiceTest {

    private static final Long VIEWER_ID = 1L;
    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    @Mock
    private DatingUnlockChargeService chargeService;
    @Mock
    private WalletService walletService;
    @Mock
    private DatingPhotoService photoService;
    @Mock
    private DatingReasonService reasonService;

    private DatingUnlockService unlockService() {
        return new DatingUnlockService(chargeService, walletService, photoService, reasonService);
    }

    private DatingProfile candidate() {
        DatingPhoto photo = new DatingPhoto(2L, "dating-photos/2/a.jpg");
        return new DatingProfile(2L, "candidate@dgu.ac.kr", "김후보", ContactMethod.PHONE,
                "010-0000-0000", "컴퓨터공학과", "ISTP", "안녕하세요", photo);
    }

    @Test
    void NAME_해금은_후보_이름을_그대로_반환한다() {
        DatingProfile candidate = candidate();
        when(chargeService.chargeAndMarkUnlocked(VIEWER_ID, CANDIDATE_ID, DatingUnlockField.NAME))
                .thenReturn(candidate);
        when(walletService.getBalance(VIEWER_ID)).thenReturn(18);

        DatingUnlockResponse response = unlockService().unlock(VIEWER_ID, CANDIDATE_ID, DatingUnlockField.NAME);

        assertThat(response.field()).isEqualTo("NAME");
        assertThat(response.value()).isEqualTo("김후보");
        assertThat(response.balance()).isEqualTo(18);
        verifyNoInteractions(reasonService, photoService);
    }

    @Test
    void PHOTO_해금은_원본_서명_URL을_반환한다() {
        DatingProfile candidate = candidate();
        when(chargeService.chargeAndMarkUnlocked(VIEWER_ID, CANDIDATE_ID, DatingUnlockField.PHOTO))
                .thenReturn(candidate);
        when(photoService.originalUrl(candidate.getPhoto())).thenReturn("https://signed-url/original.jpg");
        when(walletService.getBalance(VIEWER_ID)).thenReturn(8);

        DatingUnlockResponse response = unlockService().unlock(VIEWER_ID, CANDIDATE_ID, DatingUnlockField.PHOTO);

        assertThat(response.value()).isEqualTo("https://signed-url/original.jpg");
    }

    @Test
    void REASON_해금은_DatingReasonService_를_호출한다() {
        DatingProfile candidate = candidate();
        when(chargeService.chargeAndMarkUnlocked(VIEWER_ID, CANDIDATE_ID, DatingUnlockField.REASON))
                .thenReturn(candidate);
        when(reasonService.getOrCreate(VIEWER_ID, CANDIDATE_ID)).thenReturn("두 분은 나무와 불의 기운이라...");
        when(walletService.getBalance(VIEWER_ID)).thenReturn(15);

        DatingUnlockResponse response = unlockService().unlock(VIEWER_ID, CANDIDATE_ID, DatingUnlockField.REASON);

        assertThat(response.value()).isEqualTo("두 분은 나무와 불의 기운이라...");
    }
}

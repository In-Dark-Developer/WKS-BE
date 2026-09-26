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

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
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
        when(chargeService.chargeAndMarkUnlocked(VIEWER_ID, CANDIDATE_ID, EnumSet.of(DatingUnlockField.NAME)))
                .thenReturn(candidate);
        when(walletService.getBalance(VIEWER_ID)).thenReturn(18);

        DatingUnlockResponse response = unlockService().unlock(VIEWER_ID, CANDIDATE_ID,
                List.of(DatingUnlockField.NAME));

        assertThat(response.values()).containsExactly(entry("NAME", "김후보"));
        assertThat(response.balance()).isEqualTo(18);
        verifyNoInteractions(reasonService, photoService);
    }

    @Test
    void PHOTO_해금은_원본_서명_URL을_반환한다() {
        DatingProfile candidate = candidate();
        when(chargeService.chargeAndMarkUnlocked(VIEWER_ID, CANDIDATE_ID, EnumSet.of(DatingUnlockField.PHOTO)))
                .thenReturn(candidate);
        when(photoService.originalUrl(candidate.getPhoto())).thenReturn("https://signed-url/original.jpg");
        when(walletService.getBalance(VIEWER_ID)).thenReturn(8);

        DatingUnlockResponse response = unlockService().unlock(VIEWER_ID, CANDIDATE_ID,
                List.of(DatingUnlockField.PHOTO));

        assertThat(response.values().get("PHOTO")).isEqualTo("https://signed-url/original.jpg");
    }

    @Test
    void REASON_해금은_DatingReasonService_를_호출한다() {
        DatingProfile candidate = candidate();
        when(chargeService.chargeAndMarkUnlocked(VIEWER_ID, CANDIDATE_ID, EnumSet.of(DatingUnlockField.REASON)))
                .thenReturn(candidate);
        when(reasonService.getOrCreate(VIEWER_ID, CANDIDATE_ID)).thenReturn("두 분은 나무와 불의 기운이라...");
        when(walletService.getBalance(VIEWER_ID)).thenReturn(15);

        DatingUnlockResponse response = unlockService().unlock(VIEWER_ID, CANDIDATE_ID,
                List.of(DatingUnlockField.REASON));

        assertThat(response.values().get("REASON")).isEqualTo("두 분은 나무와 불의 기운이라...");
    }

    @Test
    void 여러_필드를_한번에_해금하고_중복은_한번으로_친다() {
        DatingProfile candidate = candidate();
        when(chargeService.chargeAndMarkUnlocked(VIEWER_ID, CANDIDATE_ID,
                EnumSet.of(DatingUnlockField.NAME, DatingUnlockField.DEPARTMENT))).thenReturn(candidate);
        when(walletService.getBalance(VIEWER_ID)).thenReturn(3);

        DatingUnlockResponse response = unlockService().unlock(VIEWER_ID, CANDIDATE_ID,
                List.of(DatingUnlockField.DEPARTMENT, DatingUnlockField.NAME, DatingUnlockField.NAME));

        // 요청 순서와 무관하게 enum 순서(PHOTO·NAME·DEPARTMENT·REASON)로 나온다
        assertThat(response.values()).containsExactly(
                entry("NAME", "김후보"),
                entry("DEPARTMENT", "컴퓨터공학과"));
        assertThat(response.balance()).isEqualTo(3);
    }
}

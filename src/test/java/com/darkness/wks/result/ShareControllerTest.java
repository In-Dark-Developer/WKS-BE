package com.darkness.wks.result;

import com.darkness.wks.result.dto.SharedResultResponse;
import com.darkness.wks.saju.Zodiac;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock
    private ResultService resultService;

    @InjectMocks
    private ShareController shareController;

    @Test
    void returnsSharedResult() {
        String shareId = UUID.randomUUID().toString();
        SharedResultResponse response = new SharedResultResponse(
                "서연",
                Zodiac.HORSE,
                new com.darkness.wks.result.dto.ResultResponse.DestinyResponse("제목", "설명"),
                List.of(),
                "부채",
                "팔정도",
                List.of()
        );
        when(resultService.getSharedResult(shareId)).thenReturn(response);

        var result = shareController.getSharedResult(shareId);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(response);
    }
}

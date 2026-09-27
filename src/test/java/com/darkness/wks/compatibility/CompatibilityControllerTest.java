package com.darkness.wks.compatibility;

import com.darkness.wks.compatibility.dto.CompatibilityResponse;
import com.darkness.wks.compatibility.dto.CreateCompatibilityRequest;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompatibilityControllerTest {

    @Mock
    private CompatibilityService compatibilityService;

    @InjectMocks
    private CompatibilityController compatibilityController;

    @Test
    void returnsCreatedForNewCompatibility() {
        String shareId = UUID.randomUUID().toString();
        CreateCompatibilityRequest request = new CreateCompatibilityRequest(UUID.randomUUID().toString());
        CompatibilityResponse response = new CompatibilityResponse(12L, 92, CompatibilityTier.GUIIN, "서연", "민수");
        when(compatibilityService.createCompatibility(shareId, request))
                .thenReturn(new CompatibilityService.CreationResult(response, true));

        var result = compatibilityController.createCompatibility(shareId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().success()).isTrue();
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void returnsOkForExistingCompatibility() {
        String shareId = UUID.randomUUID().toString();
        CreateCompatibilityRequest request = new CreateCompatibilityRequest(UUID.randomUUID().toString());
        CompatibilityResponse response = new CompatibilityResponse(12L, 82, CompatibilityTier.CHALTTEOK, "서연", "민수");
        when(compatibilityService.createCompatibility(shareId, request))
                .thenReturn(new CompatibilityService.CreationResult(response, false));

        var result = compatibilityController.createCompatibility(shareId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

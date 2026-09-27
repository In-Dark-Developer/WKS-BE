package com.darkness.wks.dating;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DatingControllerTest {

    @Mock
    private DatingPhotoService photoService;
    @Mock
    private DatingProfileService profileService;
    @Mock
    private DatingRecommendationService recommendationService;

    @InjectMocks
    private DatingController datingController;

    @Test
    void redirectsToFrontendOnSuccessfulEmailVerify() {
        ReflectionTestUtils.setField(datingController, "verifyRedirectUrl", "http://localhost:3000/dating/verify");

        var result = datingController.verifyEmail("token");

        verify(profileService).verifyEmail("token");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(result.getHeaders().getLocation()).isEqualTo(java.net.URI.create("http://localhost:3000/dating/verify"));
    }
}

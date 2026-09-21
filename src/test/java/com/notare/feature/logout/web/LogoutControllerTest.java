package com.notare.feature.logout.web;

import com.notare.feature.logout.dto.response.LogoutResponse;
import com.notare.feature.logout.service.LogoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test: the controller's path uses a ${app.title} placeholder, so it is called directly. */
@ExtendWith(MockitoExtension.class)
class LogoutControllerTest {

    @Mock private LogoutService logoutService;
    @InjectMocks private LogoutController controller;

    @Test
    void logout_stripsTheBearerPrefix_andReturnsTheServiceStatus() {
        when(logoutService.logout("abc")).thenReturn(new LogoutResponse(200, "Logout successfully"));

        ResponseEntity<LogoutResponse> response = controller.logout("Bearer abc");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(logoutService).logout("abc");
    }

    @Test
    void logout_withoutBearerPrefix_passesNullToTheService_soItAnswers400() {
        when(logoutService.logout(null)).thenReturn(new LogoutResponse(400, "Access token is missing"));

        ResponseEntity<LogoutResponse> response = controller.logout("abc");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}

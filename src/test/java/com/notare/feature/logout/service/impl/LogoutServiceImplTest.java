package com.notare.feature.logout.service.impl;

import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.feature.logout.dto.response.LogoutResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Tests for LogoutServiceImpl. */
@ExtendWith(MockitoExtension.class)
class LogoutServiceImplTest {

    @Mock private InternalAuthClient authClient;
    @InjectMocks private LogoutServiceImpl service;

    @Test
    void logout_endsTheSessionAtIam_andReturns200() {
        LogoutResponse response = service.logout("access-1");

        verify(authClient).logout("access-1");
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    void logout_withBlankToken_returns400_withoutCallingIam() {
        assertThat(service.logout(" ").getStatusCode()).isEqualTo(400);
        assertThat(service.logout(null).getStatusCode()).isEqualTo(400);

        verifyNoInteractions(authClient);
    }

    @Test
    void logout_doesNotSwallowIamFailures() {
        doThrow(new InternalAuthCallException(HttpStatus.BAD_GATEWAY, "down")).when(authClient).logout("access-1");

        assertThatThrownBy(() -> service.logout("access-1")).isInstanceOf(InternalAuthCallException.class);
    }
}

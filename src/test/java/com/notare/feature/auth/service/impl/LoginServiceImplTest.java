package com.notare.feature.auth.service.impl;

import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.InternalAuthLoginResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.UserLoginResponse;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for LoginServiceImpl. */
@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock private InternalAuthClient authClient;
    @Mock private UserRepository userRepository;
    @InjectMocks private LoginServiceImpl service;

    private LoginRequest request(String identifier, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail(identifier);
        request.setPassword(password);
        return request;
    }

    private User user(UserStatus status, boolean emailVerified) {
        User user = new User();
        user.setId(42L);
        user.setUsername("jdoe");
        user.setEmail("jdoe@example.com");
        user.setNotarianId("NOT-ABCDEF");
        user.setUserStatus(status);
        user.setEmailVerified(emailVerified);
        return user;
    }

    private InternalAuthApiResponse<InternalAuthLoginResponse> iamOk() {
        return InternalAuthApiResponse.success("Login successful.",
                new InternalAuthLoginResponse(1L, "jdoe", "Login successful.", false, "access-1", "refresh-1", 900L, 2592000L));
    }

    @Test
    void login_returnsIamTokens_withExpiryInMilliseconds() {
        when(userRepository.findByUsernameOrEmail("jdoe", "jdoe")).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(authClient.login(any())).thenReturn(iamOk());

        UserLoginResponse response = service.login(request("jdoe", "Secret123!"), "UA", "1.2.3.4");

        assertThat(response.getAccessToken()).isEqualTo("access-1");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-1");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900_000L);
        assertThat(response.getNotarianId()).isEqualTo("NOT-ABCDEF");
        assertThat(response.getUsername()).isEqualTo("jdoe");
    }

    @Test
    void login_withEmail_sendsTheResolvedUsernameToIam() {
        when(userRepository.findByUsernameOrEmail("jdoe@example.com", "jdoe@example.com"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(authClient.login(any())).thenReturn(iamOk());

        service.login(request("  jdoe@example.com ", "Secret123!"), "UA", "1.2.3.4");

        ArgumentCaptor<com.notare.client.dto.request.LoginRequest> captor =
                ArgumentCaptor.forClass(com.notare.client.dto.request.LoginRequest.class);
        verify(authClient).login(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("jdoe");
        assertThat(captor.getValue().getPassword()).isEqualTo("Secret123!");
    }

    @Test
    void login_unknownUser_throwsBadCredentials_withoutCallingIam() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request("ghost", "x"), "UA", "ip"))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(authClient);
    }

    @Test
    void login_lockedUser_throwsLocked_withoutCallingIam() {
        when(userRepository.findByUsernameOrEmail("jdoe", "jdoe")).thenReturn(Optional.of(user(UserStatus.LOCKED, true)));

        assertThatThrownBy(() -> service.login(request("jdoe", "x"), "UA", "ip"))
                .isInstanceOf(LockedException.class);

        verifyNoInteractions(authClient);
    }

    @Test
    void login_locallyUnverifiedUser_isLoggedInAndMarkedVerified_whenIamAccepts() {
        User unverified = user(UserStatus.ACTIVE, false);
        when(userRepository.findByUsernameOrEmail("jdoe", "jdoe")).thenReturn(Optional.of(unverified));
        when(authClient.login(any())).thenReturn(iamOk());

        UserLoginResponse response = service.login(request("jdoe", "Secret123!"), "UA", "ip");

        assertThat(response.getAccessToken()).isEqualTo("access-1");
        assertThat(unverified.isEmailVerified()).isTrue();
        verify(userRepository).save(unverified);
    }

    @Test
    void login_iamRejectsCredentials_propagatesTheIamError() {
        when(userRepository.findByUsernameOrEmail("jdoe", "jdoe")).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(authClient.login(any())).thenThrow(new InternalAuthCallException(HttpStatus.UNAUTHORIZED, "Username or password is incorrect"));

        assertThatThrownBy(() -> service.login(request("jdoe", "bad"), "UA", "ip"))
                .isInstanceOf(InternalAuthCallException.class)
                .hasMessage("Username or password is incorrect");
    }

    @Test
    void login_iamAsksForMfa_isReportedAsBadGateway() {
        when(userRepository.findByUsernameOrEmail("jdoe", "jdoe")).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(authClient.login(any())).thenReturn(InternalAuthApiResponse.success("otp sent",
                new InternalAuthLoginResponse(null, "jdoe", "otp sent", true, null, null, null, null)));

        assertThatThrownBy(() -> service.login(request("jdoe", "x"), "UA", "ip"))
                .isInstanceOfSatisfying(InternalAuthCallException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void login_iamReturnsNoBody_isReportedAsBadGateway() {
        when(userRepository.findByUsernameOrEmail("jdoe", "jdoe")).thenReturn(Optional.of(user(UserStatus.ACTIVE, true)));
        when(authClient.login(any())).thenReturn(null);

        assertThatThrownBy(() -> service.login(request("jdoe", "x"), "UA", "ip"))
                .isInstanceOf(InternalAuthCallException.class);
    }
}

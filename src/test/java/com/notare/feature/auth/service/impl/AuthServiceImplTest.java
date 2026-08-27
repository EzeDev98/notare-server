package com.notare.feature.auth.service.impl;

import com.notare.common.exception.InvalidRefreshTokenException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.common.security.JwtService;
import com.notare.feature.auth.dto.request.RefreshTokenRequest;
import com.notare.feature.auth.dto.response.TokenResponse;
import com.notare.feature.session.records.RotatedSession;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.session.service.impl.SessionServiceImplTest;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.repository.UserRepository;
import com.notare.feature.user.service.impl.UserServiceImplementationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 5f of the coverage roadmap.
 *
 * <p><b>Correction on the roadmap's own wording:</b> {@link AuthServiceImpl}
 * implements only the refresh-token flow ({@link AuthServiceImpl#refreshToken}).
 * It does NOT handle registration - that lives entirely in {@code
 * UserServiceImplementation.registerUser} (covered under Step 5a). The
 * roadmap entry describing 5f as "registration orchestration" was inaccurate;
 * this file covers what the class actually does.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito is unavailable here (see
 * {@link UserServiceImplementationTest}
 * for the full explanation). Reasoned from source; run via {@code mvn test}
 * before trusting it.
 *
 * <p>Covers {@link AuthServiceImpl#refreshToken}: rotation is delegated
 * entirely to {@link SessionService#rotateRefreshToken} (this class does not
 * re-implement or duplicate any of that token-theft/reuse logic - see {@link
 * SessionServiceImplTest} for that),
 * a rotated session whose {@code userId} no longer resolves to a row throws
 * {@link UserNotFoundException} (an account deleted between token issuance
 * and refresh), and the new access token is minted with the freshly-rotated
 * session id under the {@code sid} claim - NOT the session id that was on the
 * presented (now-invalidated) token, since {@code rotateRefreshToken}'s
 * session id is the authoritative one here (in this app the session id does
 * not actually change on rotation, but the class always reads it off the
 * rotation result rather than assuming that).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final long EXPIRATION_MS = 3_600_000L;

    @Mock private SessionService sessionService;
    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;

    private AuthServiceImpl newService() throws Exception {
        AuthServiceImpl impl = new AuthServiceImpl(sessionService, userRepository, jwtService);
        Field field = AuthServiceImpl.class.getDeclaredField("expirationMs");
        field.setAccessible(true);
        field.setLong(impl, EXPIRATION_MS);
        return impl;
    }

    private RefreshTokenRequest refreshTokenRequest(String token) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);
        return request;
    }

    @Test
    void refreshToken_delegatesRotationEntirelyToSessionService() throws Exception {
        AuthServiceImpl service = newService();
        when(sessionService.rotateRefreshToken("session-1:secret", "ua", "ip"))
                .thenReturn(new RotatedSession("session-1", "session-1:newsecret", 5L));
        User user = new User();
        user.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        service.refreshToken(refreshTokenRequest("session-1:secret"), "ua", "ip");

        verify(sessionService).rotateRefreshToken("session-1:secret", "ua", "ip");
    }

    @Test
    void refreshToken_propagatesInvalidRefreshTokenException_withoutTouchingUserRepositoryOrJwtService() throws Exception {
        AuthServiceImpl service = newService();
        InvalidRefreshTokenException rejection = new InvalidRefreshTokenException("Refresh token invalid; session revoked.");
        when(sessionService.rotateRefreshToken(any(), any(), any())).thenThrow(rejection);

        assertThatThrownBy(() -> service.refreshToken(refreshTokenRequest("bad:token"), "ua", "ip"))
                .isSameAs(rejection);

        verifyNoInteractions(userRepository, jwtService);
    }

    @Test
    void refreshToken_throwsUserNotFoundException_whenTheRotatedSessionsUserNoLongerExists() throws Exception {
        AuthServiceImpl service = newService();
        when(sessionService.rotateRefreshToken(any(), any(), any()))
                .thenReturn(new RotatedSession("session-1", "session-1:newsecret", 404L));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshToken(refreshTokenRequest("session-1:secret"), "ua", "ip"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found");

        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void refreshToken_generatesTheNewAccessTokenWithTheRotatedSessionIdUnderTheSidClaim() throws Exception {
        AuthServiceImpl service = newService();
        when(sessionService.rotateRefreshToken(any(), any(), any()))
                .thenReturn(new RotatedSession("rotated-session-id", "rotated-session-id:newsecret", 5L));
        User user = new User();
        user.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        service.refreshToken(refreshTokenRequest("session-1:secret"), "ua", "ip");

        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<UserDetail> principalCaptor = ArgumentCaptor.forClass(UserDetail.class);
        verify(jwtService).generateToken(claimsCaptor.capture(), principalCaptor.capture());
        assertThat(claimsCaptor.getValue()).containsEntry("sid", "rotated-session-id");
        assertThat(principalCaptor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void refreshToken_returnsATokenResponseCombiningTheNewAccessTokenAndTheRotatedRefreshToken() throws Exception {
        AuthServiceImpl service = newService();
        when(sessionService.rotateRefreshToken(any(), any(), any()))
                .thenReturn(new RotatedSession("session-1", "session-1:brandnewsecret", 5L));
        User user = new User();
        user.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        TokenResponse response = service.refreshToken(refreshTokenRequest("session-1:secret"), "ua", "ip");

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("session-1:brandnewsecret");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(EXPIRATION_MS);
    }
}
package com.notare.feature.auth.service.impl;

import com.notare.common.security.JwtService;
import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.LoginResponse;
import com.notare.feature.session.records.SessionCreationResult;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 5c of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito and Spring Security are
 * unavailable here (see {@link com.notare.feature.user.service.impl.UserServiceImplementationTest}
 * for the full explanation). Reasoned from source; run via {@code mvn test}
 * before trusting it.
 *
 * <p>Covers {@link LoginServiceImpl#login}: the identifier is trimmed before
 * it reaches {@code AuthenticationManager} (so " johndoe " and "johndoe"
 * behave the same), authentication failure propagates as whatever
 * {@code AuthenticationException} Spring Security threw (BadCredentials,
 * Disabled, Locked - this class does not catch or translate any of them, so
 * this suite does not re-assert Spring Security's own behavior, only that
 * nothing here swallows it), a session is created for the authenticated
 * user's numeric id (not username - the id is what the session/audit trail
 * is keyed on), the JWT carries the session id under the {@code sid} claim,
 * and the response mixes data from three different sources: the token from
 * {@code JwtService}, the raw refresh token from {@code SessionService}, and
 * the notarianId/username from the authenticated principal.
 *
 * <p>{@code expirationMs} is a private field populated by Spring's {@code
 * @Value} injection in production, with no setter for a plain unit test to
 * use instead. Reflection ({@code setAccessible(true)}) sets it directly so
 * this suite does not have to depend on Spring's property-placeholder
 * resolution just to pin down a single long value.
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    private static final long EXPIRATION_MS = 3_600_000L;

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private SessionService sessionService;

    private LoginServiceImpl service;

    private LoginServiceImpl newService() throws Exception {
        LoginServiceImpl impl = new LoginServiceImpl(authenticationManager, jwtService, sessionService);
        Field field = LoginServiceImpl.class.getDeclaredField("expirationMs");
        field.setAccessible(true);
        field.setLong(impl, EXPIRATION_MS);
        return impl;
    }

    private UserDetail userDetail(Long id, String username, String notarianId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNotarianId(notarianId);
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return new UserDetail(user);
    }

    private LoginRequest loginRequest(String identifier, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail(identifier);
        request.setPassword(password);
        return request;
    }

    @Test
    void login_trimsTheIdentifierBeforeAuthenticating() throws Exception {
        service = newService();
        UserDetail detail = userDetail(1L, "johndoe", "BADAZOP");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(detail);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(sessionService.createSession(eq(1L), anyString(), anyString()))
                .thenReturn(new SessionCreationResult("sess-1", "sess-1:secret"));
        when(jwtService.generateToken(org.mockito.ArgumentMatchers.anyMap(), eq(detail))).thenReturn("jwt-token");

        service.login(loginRequest("  johndoe  ", "Passw0rd!"), "ua", "127.0.0.1");

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("johndoe");
        assertThat(captor.getValue().getCredentials()).isEqualTo("Passw0rd!");
    }

    @Test
    void login_createsASessionKeyedOnTheAuthenticatedUsersNumericId_notTheUsername() throws Exception {
        service = newService();
        UserDetail detail = userDetail(42L, "johndoe", "BADAZOP");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(detail);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(sessionService.createSession(eq(42L), eq("Mozilla/5.0"), eq("203.0.113.7")))
                .thenReturn(new SessionCreationResult("sess-1", "sess-1:secret"));
        when(jwtService.generateToken(org.mockito.ArgumentMatchers.anyMap(), eq(detail))).thenReturn("jwt-token");

        service.login(loginRequest("johndoe", "Passw0rd!"), "Mozilla/5.0", "203.0.113.7");

        verify(sessionService).createSession(42L, "Mozilla/5.0", "203.0.113.7");
    }

    @Test
    void login_generatesTheTokenWithTheSessionIdUnderTheSidClaim() throws Exception {
        service = newService();
        UserDetail detail = userDetail(1L, "johndoe", "BADAZOP");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(detail);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(sessionService.createSession(any(), anyString(), anyString()))
                .thenReturn(new SessionCreationResult("session-xyz", "session-xyz:secret"));
        when(jwtService.generateToken(any(), eq(detail))).thenReturn("jwt-token");

        service.login(loginRequest("johndoe", "Passw0rd!"), "ua", "ip");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtService).generateToken(claimsCaptor.capture(), eq(detail));
        assertThat(claimsCaptor.getValue()).containsEntry("sid", "session-xyz");
    }

    @Test
    void login_returnsAResponseAssembledFromTheTokenSessionAndPrincipal() throws Exception {
        service = newService();
        UserDetail detail = userDetail(1L, "johndoe", "BADAZOP");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(detail);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(sessionService.createSession(any(), anyString(), anyString()))
                .thenReturn(new SessionCreationResult("session-xyz", "session-xyz:rawsecret"));
        when(jwtService.generateToken(any(), eq(detail))).thenReturn("jwt-token");

        LoginResponse response = service.login(loginRequest("johndoe", "Passw0rd!"), "ua", "ip");

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("session-xyz:rawsecret");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(EXPIRATION_MS);
        assertThat(response.getNotarianId()).isEqualTo("BADAZOP");
        assertThat(response.getUsername()).isEqualTo("johndoe");
    }

    @Test
    void login_propagatesWhateverAuthenticationExceptionTheManagerThrows() throws Exception {
        service = newService();
        org.springframework.security.authentication.BadCredentialsException authEx =
                new org.springframework.security.authentication.BadCredentialsException("bad credentials");
        when(authenticationManager.authenticate(any())).thenThrow(authEx);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.login(loginRequest("johndoe", "wrong"), "ua", "ip"))
                .isSameAs(authEx);

        org.mockito.Mockito.verifyNoInteractions(sessionService, jwtService);
    }
}
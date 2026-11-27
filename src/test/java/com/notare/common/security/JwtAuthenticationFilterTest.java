package com.notare.common.security;

import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.service.impl.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for JwtAuthenticationFilter. */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtDecoder jwtDecoder;
    @Mock private CustomUserDetailsService userDetailsService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter filter(String clientApplicationId) {
        return new JwtAuthenticationFilter(jwtDecoder, userDetailsService, clientApplicationId);
    }

    private Jwt jwt(String username, Object clientApplicationId) {
        Jwt.Builder builder = Jwt.withTokenValue("tok").header("alg", "none")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).subject("iam-1");
        if (username != null) builder.claim("username", username);
        if (clientApplicationId != null) builder.claim("client_application_id", clientApplicationId);
        return builder.build();
    }

    private UserDetail userDetail(UserStatus status, boolean emailVerified) {
        User user = new User();
        user.setId(42L);
        user.setUsername("jdoe");
        user.setUserStatus(status);
        user.setEmailVerified(emailVerified);
        return new UserDetail(user);
    }

    private boolean run(JwtAuthenticationFilter filter, String authHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authHeader != null) request.addHeader("Authorization", authHeader);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    private boolean authenticated() {
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test
    void noAuthorizationHeader_passesThroughUnauthenticated() throws Exception {
        assertThat(run(filter(""), null)).isTrue();
        assertThat(authenticated()).isFalse();
        verifyNoInteractions(jwtDecoder, userDetailsService);
    }

    @Test
    void nonBearerHeader_passesThroughUnauthenticated() throws Exception {
        assertThat(run(filter(""), "Basic abc")).isTrue();
        assertThat(authenticated()).isFalse();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void validToken_authenticatesTheLocalUser() throws Exception {
        when(jwtDecoder.decode("tok")).thenReturn(jwt("jdoe", null));
        when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(userDetail(UserStatus.ACTIVE, true));

        assertThat(run(filter(""), "Bearer tok")).isTrue();

        assertThat(authenticated()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("jdoe");
    }

    @Test
    void rejectedToken_continuesUnauthenticated() throws Exception {
        when(jwtDecoder.decode("tok")).thenThrow(new BadJwtException("expired"));

        assertThat(run(filter(""), "Bearer tok")).isTrue();

        assertThat(authenticated()).isFalse();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void tokenWithoutUsernameClaim_isNotAuthenticated() throws Exception {
        when(jwtDecoder.decode("tok")).thenReturn(jwt(null, null));

        assertThat(run(filter(""), "Bearer tok")).isTrue();

        assertThat(authenticated()).isFalse();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void validTokenForUnknownLocalUser_isNotAuthenticated() throws Exception {
        when(jwtDecoder.decode("tok")).thenReturn(jwt("ghost", null));
        when(userDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("none"));

        assertThat(run(filter(""), "Bearer tok")).isTrue();

        assertThat(authenticated()).isFalse();
    }

    @Test
    void lockedLocalUser_isNotAuthenticated() throws Exception {
        when(jwtDecoder.decode("tok")).thenReturn(jwt("jdoe", null));
        when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(userDetail(UserStatus.LOCKED, true));

        assertThat(run(filter(""), "Bearer tok")).isTrue();

        assertThat(authenticated()).isFalse();
    }

    @Test
    void unverifiedLocalUser_isNotAuthenticated() throws Exception {
        when(jwtDecoder.decode("tok")).thenReturn(jwt("jdoe", null));
        when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(userDetail(UserStatus.ACTIVE, false));

        assertThat(run(filter(""), "Bearer tok")).isTrue();

        assertThat(authenticated()).isFalse();
    }

    @Test
    void tokenForAnotherClientApplication_isRejectedWhenClientIdIsPinned() throws Exception {
        when(jwtDecoder.decode("tok")).thenReturn(jwt("jdoe", 99L));

        assertThat(run(filter("7"), "Bearer tok")).isTrue();

        assertThat(authenticated()).isFalse();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void tokenForThisClientApplication_isAcceptedWhenClientIdIsPinned() throws Exception {
        when(jwtDecoder.decode("tok")).thenReturn(jwt("jdoe", 7L));
        when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(userDetail(UserStatus.ACTIVE, true));

        assertThat(run(filter("7"), "Bearer tok")).isTrue();

        assertThat(authenticated()).isTrue();
    }
}

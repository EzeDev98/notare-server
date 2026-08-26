package com.notare.common.security;

import com.notare.feature.session.service.SessionService;
import com.notare.feature.user.service.impl.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 4b of the coverage roadmap.
 *
 * <p>Covers {@link JwtAuthenticationFilter#doFilterInternal}: the two
 * fall-through cases (no header, non-Bearer header) that must never touch
 * {@code JwtService}, the full happy path that ends in an authenticated
 * {@code SecurityContextHolder}, the two independent reasons authentication
 * can be withheld even with a syntactically valid token (an inactive session,
 * {@code isTokenValid} returning false), the catch block that clears context
 * on a thrown {@code JwtException}/{@code UsernameNotFoundException} rather
 * than letting the request fail outright, and the re-entrancy guard - if
 * something upstream already authenticated this request, this filter must
 * not overwrite it.
 *
 * <p>{@code SecurityContextHolder} is a static, thread-local singleton, so
 * every test clears it in {@code @AfterEach} - without that, an
 * authentication set by one test would leak into the next one's "should stay
 * null" assertions.
 *
 * <p>Verification note: not compiled or executed in this sandbox (no route to
 * the jakarta.servlet-api / spring-security-web jars) - reasoned from the
 * source, same caveat as Step 4a.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private SessionService sessionService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private UserDetails userDetails;

    private JwtAuthenticationFilter filter;

    private static final String SESSION_ID = "sess-123";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, sessionService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void doFilter() throws Exception {
        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void doFilter_noAuthorizationHeaderPassesThroughWithoutAuthenticating() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService, sessionService);
    }

    @Test
    void doFilter_nonBearerHeaderPassesThroughWithoutAuthenticating() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService, sessionService);
    }

    @Test
    void doFilter_validTokenAndActiveSessionAuthenticatesTheRequest() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.extractUsername("good-token")).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);
        when(jwtService.extractSessionId("good-token")).thenReturn(SESSION_ID);
        when(jwtService.isTokenValid("good-token", userDetails)).thenReturn(true);
        when(sessionService.isSessionActive(SESSION_ID)).thenReturn(true);

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userDetails);
        verify(request).setAttribute(JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, SESSION_ID);
        verify(sessionService).touch(SESSION_ID);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_validTokenButInactiveSessionDoesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.extractUsername("good-token")).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);
        when(jwtService.extractSessionId("good-token")).thenReturn(SESSION_ID);
        when(jwtService.isTokenValid("good-token", userDetails)).thenReturn(true);
        when(sessionService.isSessionActive(SESSION_ID)).thenReturn(false);

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(sessionService, never()).touch(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_isTokenValidFalseDoesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer stale-token");
        when(jwtService.extractUsername("stale-token")).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);
        when(jwtService.extractSessionId("stale-token")).thenReturn(SESSION_ID);
        when(jwtService.isTokenValid("stale-token", userDetails)).thenReturn(false);

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(sessionService, never()).isSessionActive(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_jwtExceptionIsCaughtAndClearsContextButStillContinuesTheChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        when(jwtService.extractUsername("expired-token")).thenThrow(new JwtException("expired"));

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_unknownUserIsCaughtAndClearsContextButStillContinuesTheChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.extractUsername("good-token")).thenReturn("ghost@example.com");
        when(userDetailsService.loadUserByUsername("ghost@example.com"))
                .thenThrow(new UsernameNotFoundException("no such user"));

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_alreadyAuthenticatedContextIsNeverOverwrittenOrRequeried() throws Exception {
        // Simulates a request that's already authenticated by the time this
        // filter runs. The `SecurityContextHolder...getAuthentication() == null`
        // guard means the whole block - including the user/session lookups -
        // must be skipped entirely.
        UsernamePasswordAuthenticationToken existing =
                new UsernamePasswordAuthenticationToken("someone", null);
        SecurityContextHolder.getContext().setAuthentication(existing);

        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.extractUsername("good-token")).thenReturn("alice@example.com");

        doFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verifyNoInteractions(userDetailsService, sessionService);
        verify(filterChain).doFilter(request, response);
    }
}
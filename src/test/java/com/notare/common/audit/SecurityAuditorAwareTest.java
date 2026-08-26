package com.notare.common.audit;

import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step 4c of the coverage roadmap (part 1 of 2 - see also {@link
 * SystemAuditorTest}).
 *
 * <p>Covers {@link SecurityAuditorAware#getCurrentAuditor()} against every
 * branch its own doc comment calls out, in order of how easy each is to get
 * wrong:
 *
 * <ol>
 *   <li>No authentication at all -> system auditor.</li>
 *   <li><b>An anonymous request</b> -> system auditor. This is the one the
 *       class doc comment calls "the trap": Spring Security installs an
 *       {@code AnonymousAuthenticationToken} for unauthenticated requests
 *       rather than leaving {@code getAuthentication()} null, and that token
 *       reports {@code isAuthenticated() == true}. A naive
 *       {@code authentication != null && authentication.isAuthenticated()}
 *       check would pass for it and then try to read a user id off the
 *       string {@code "anonymousUser"}. This is the single most important
 *       test in this class.</li>
 *   <li>An authenticated-but-non-UserDetail principal (a type this method
 *       cannot safely cast) -> system auditor, not a ClassCastException.</li>
 *   <li>An authenticated request whose principal really is a
 *       {@link UserDetail} -> that user's own id.</li>
 * </ol>
 *
 * <p>Every test clears {@code SecurityContextHolder} in {@code @AfterEach} -
 * it's a static thread-local, and leaving a stale {@code Authentication} in
 * place would leak into unrelated tests elsewhere in the suite.
 */
class SecurityAuditorAwareTest {

    private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentAuditor_returnsSystemAuditorIdWhenThereIsNoAuthenticationAtAll() {
        SecurityContextHolder.clearContext(); // belt and suspenders: explicit "no auth" state

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).contains(SystemAuditor.ID);
    }

    @Test
    void getCurrentAuditor_returnsSystemAuditorIdForAnAnonymousAuthenticationToken() {
        // isAuthenticated() is TRUE on this token despite there being no real
        // user - the exact trap the class doc comment describes.
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(anonymous.isAuthenticated()).isTrue(); // sanity-check the trap itself

        SecurityContextHolder.getContext().setAuthentication(anonymous);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).contains(SystemAuditor.ID);
    }

    @Test
    void getCurrentAuditor_returnsSystemAuditorIdWhenAuthenticationIsNotAuthenticated() {
        Authentication unauthenticated = mock(Authentication.class);
        when(unauthenticated.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).contains(SystemAuditor.ID);
    }

    @Test
    void getCurrentAuditor_returnsSystemAuditorIdWhenThePrincipalIsNotAUserDetail() {
        // Authenticated, not anonymous, but the principal is some other type
        // (Spring's own default "user" String, or a filter installing its
        // own principal type). Must fall back safely, never cast-and-throw.
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                "some-other-principal-type", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authenticated);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).contains(SystemAuditor.ID);
    }

    @Test
    void getCurrentAuditor_returnsTheUsersOwnIdWhenThePrincipalIsARealUserDetail() {
        User user = new User();
        user.setId(42L);
        UserDetail userDetail = new UserDetail(user);

        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                userDetail, null, userDetail.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authenticated);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).contains(42L);
    }

    @Test
    void getCurrentAuditor_neverReturnsAnEmptyOptional() {
        // The class doc comment is explicit that this is the whole point of
        // the sentinel value - every branch must return SOMETHING.
        SecurityContextHolder.clearContext();
        assertThat(auditorAware.getCurrentAuditor()).isPresent();

        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(auditorAware.getCurrentAuditor()).isPresent();
    }
}
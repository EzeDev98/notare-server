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

/** Tests for SecurityAuditorAware. */
class SecurityAuditorAwareTest {

    private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentAuditor_returnsSystemAuditorIdWhenThereIsNoAuthenticationAtAll() {
        SecurityContextHolder.clearContext();

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).contains(SystemAuditor.ID);
    }

    @Test
    void getCurrentAuditor_returnsSystemAuditorIdForAnAnonymousAuthenticationToken() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(anonymous.isAuthenticated()).isTrue();

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
        SecurityContextHolder.clearContext();
        assertThat(auditorAware.getCurrentAuditor()).isPresent();

        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(auditorAware.getCurrentAuditor()).isPresent();
    }
}

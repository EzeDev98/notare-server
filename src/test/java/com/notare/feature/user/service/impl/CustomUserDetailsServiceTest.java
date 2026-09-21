package com.notare.feature.user.service.impl;

import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 5b of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito and Spring Security are
 * unavailable as compile-time dependencies here (see the note in {@link
 * UserServiceImplementationTest}). Reasoned from source; run via
 * {@code mvn test} before trusting it.
 *
 * <p>Covers {@link CustomUserDetailsService#loadUserByUsername}. Small class,
 * but two things are worth pinning down explicitly: (1) the SAME value the
 * caller passed in is used for both the username and the email argument of
 * {@code findByUsernameOrEmail} - a login form has one text field, and the
 * class relies entirely on the repository query to figure out which column
 * it matches; (2) a miss produces {@link UsernameNotFoundException} with a
 * message that does not repeat back whatever the caller typed, so a
 * brute-forcer cannot use the error to distinguish "wrong username" from
 * "wrong password".
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    private CustomUserDetailsService newService() {
        return new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_passesTheSameValueForBothUsernameAndEmailArguments() {
        User user = new User();
        user.setId(1L);
        user.setUsername("johndoe");
        when(userRepository.findByUsernameOrEmail("johndoe", "johndoe")).thenReturn(Optional.of(user));

        newService().loadUserByUsername("johndoe");

        verify(userRepository).findByUsernameOrEmail("johndoe", "johndoe");
    }

    @Test
    void loadUserByUsername_worksWhenTheCallerTypedAnEmailInstead() {
        // Same call shape either way - the repository's OR clause is what
        // decides whether "johndoe" or "john@example.com" is a match, not
        // this class.
        User user = new User();
        user.setId(1L);
        user.setUsername("johndoe");
        when(userRepository.findByUsernameOrEmail("john@example.com", "john@example.com"))
                .thenReturn(Optional.of(user));

        UserDetails result = newService().loadUserByUsername("john@example.com");

        assertThat(result).isInstanceOf(UserDetail.class);
    }

    @Test
    void loadUserByUsername_wrapsTheMatchedUserInAUserDetail() {
        User user = new User();
        user.setId(7L);
        user.setUsername("johndoe");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        when(userRepository.findByUsernameOrEmail("johndoe", "johndoe")).thenReturn(Optional.of(user));

        UserDetails result = newService().loadUserByUsername("johndoe");

        assertThat(result).isInstanceOf(UserDetail.class);
        UserDetail detail = (UserDetail) result;
        assertThat(detail.getUser()).isSameAs(user);
        assertThat(detail.getUsername()).isEqualTo("johndoe");
        assertThat(detail.getPassword()).isNull();
        assertThat(detail.isEnabled()).isTrue();
        assertThat(detail.isAccountNonLocked()).isTrue();
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFoundException_whenNoMatchExists() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No account found for the provided credentials");
    }

    @Test
    void loadUserByUsername_errorMessageNeverEchoesBackTheAttemptedIdentifier() {
        // A message that repeated the input ("No account found for 'admin@bank.com'")
        // would hand an attacker a free username/email oracle. This locks the
        // fixed, generic wording in place.
        when(userRepository.findByUsernameOrEmail("admin@bank.com", "admin@bank.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().loadUserByUsername("admin@bank.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No account found for the provided credentials")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("admin@bank.com"));
    }
}
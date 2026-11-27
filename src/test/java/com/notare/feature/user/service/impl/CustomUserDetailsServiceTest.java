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

/** Tests for CustomUserDetailsService. */
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
        when(userRepository.findByUsernameOrEmail("admin@bank.com", "admin@bank.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().loadUserByUsername("admin@bank.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No account found for the provided credentials")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("admin@bank.com"));
    }
}

package com.notare.common.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static com.notare.common.constant.GeneralConstant.SESSION_ID_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Tests for JwtService. */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final long ONE_HOUR_MS = 3_600_000L;

    @Mock
    private UserDetails userDetails;

    private static String randomBase64Secret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private JwtService newService(long expirationMs, long resetTokenExpirationMs) {
        return new JwtService(randomBase64Secret(), expirationMs, resetTokenExpirationMs);
    }

    @Test
    void generateToken_thenExtractUsername_roundTrips() {
        JwtService service = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String token = service.generateToken(userDetails);

        assertThat(service.extractUsername(token)).isEqualTo("alice@example.com");
    }

    @Test
    void generateToken_withExtraClaims_sessionIdClaimIsExtractable() {
        JwtService service = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String token = service.generateToken(Map.of(SESSION_ID_CLAIM, "sess-123"), userDetails);

        assertThat(service.extractSessionId(token)).isEqualTo("sess-123");
    }

    @Test
    void isTokenValid_trueForMatchingUsernameAndUnexpiredToken() {
        JwtService service = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String token = service.generateToken(userDetails);

        assertThat(service.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_falseForUsernameMismatch() {
        JwtService service = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");
        String token = service.generateToken(userDetails);

        UserDetails differentUser = org.mockito.Mockito.mock(UserDetails.class);
        when(differentUser.getUsername()).thenReturn("bob@example.com");

        assertThat(service.isTokenValid(token, differentUser)).isFalse();
    }

    @Test
    void extractUsername_throwsForATokenSignedWithADifferentKey() {
        JwtService serviceA = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        JwtService serviceB = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String tokenSignedByA = serviceA.generateToken(userDetails);

        assertThatThrownBy(() -> serviceB.extractUsername(tokenSignedByA))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUsername_throwsForAStringThatIsNotAJwtAtAll() {
        JwtService service = newService(ONE_HOUR_MS, ONE_HOUR_MS);

        assertThatThrownBy(() -> service.extractUsername("not-a-jwt-at-all"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generatePasswordResetToken_isMarkedAsAPasswordResetToken() {
        JwtService service = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String resetToken = service.generatePasswordResetToken(userDetails);

        assertThat(service.isPasswordResetToken(resetToken)).isTrue();
    }

    @Test
    void isPasswordResetToken_falseForAnOrdinaryAccessToken() {
        JwtService service = newService(ONE_HOUR_MS, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String accessToken = service.generateToken(userDetails);

        assertThat(service.isPasswordResetToken(accessToken)).isFalse();
    }

    @Test
    void expiredToken_extractUsernameThrowsRatherThanIsTokenExpiredReturningFalse() throws InterruptedException {
        JwtService service = newService(1L, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String token = service.generateToken(userDetails);
        Thread.sleep(25);

        assertThatThrownBy(() -> service.isTokenValid(token, userDetails))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void constructorParameters_accessAndResetExpirationAreNotTransposed() throws InterruptedException {
        JwtService service = newService(ONE_HOUR_MS, 1L);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String accessToken = service.generateToken(userDetails);
        String resetToken = service.generatePasswordResetToken(userDetails);

        Thread.sleep(25);

        assertThat(service.extractUsername(accessToken)).isEqualTo("alice@example.com");
        assertThatThrownBy(() -> service.extractUsername(resetToken)).isInstanceOf(JwtException.class);
    }
}

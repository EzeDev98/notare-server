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

/**
 * Step 4a of the coverage roadmap.
 *
 * <p>Covers {@link JwtService}: issue/parse round-tripping, the extra-claims
 * path {@code JwtAuthenticationFilter} relies on for the session id, rejecting
 * a token signed with a different key or one that isn't a JWT at all, the
 * password-reset-token marker, and the constructor's two millisecond
 * parameters ({@code expirationMs}, {@code resetTokenExpirationMs}) - two
 * adjacent {@code long} arguments of the same type, exactly where a
 * transposition hides silently.
 *
 * <p>One behavior worth reading carefully before trusting {@link
 * JwtService#isTokenValid}: its own body is
 * {@code username.equals(...) && !isTokenExpired(token)}, which LOOKS like it
 * returns {@code false} for an expired token. It does not. {@code
 * isTokenValid} calls {@code extractUsername(token)} FIRST, and JJWT's parser
 * validates the {@code exp} claim during parsing - an expired token makes
 * {@code Jwts.parser()...parseSignedClaims(token)} throw {@code
 * ExpiredJwtException} (a {@link JwtException}) before {@code isTokenValid}
 * ever reaches its own expiry check. So the private {@code isTokenExpired}/
 * {@code extractExpiration} methods are effectively unreachable dead code for
 * a genuinely-expired token: the real behavior is "throws", not "returns
 * false". The system still works correctly end to end only because {@link
 * JwtAuthenticationFilter} wraps its own call to {@code extractUsername} in a
 * try/catch - but that safety net lives in the CALLER, not in {@code
 * isTokenValid} itself. {@link
 * #expiredToken_extractUsernameThrowsRatherThanIsTokenExpiredReturningFalse()}
 * pins this down directly.
 *
 * <p>Verification note: this file could not be compiled or executed in the
 * sandbox this was written in (no route to Maven Central for jjwt /
 * spring-security-core), so unlike Steps 1 and 3 this is reasoned from the
 * source and well-established JJWT 0.13.x behavior, not run. Worth an actual
 * {@code mvn test} pass before trusting it fully - the expired-token-throws
 * claim above especially.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final long ONE_HOUR_MS = 3_600_000L;

    @Mock
    private UserDetails userDetails;

    private static String randomBase64Secret() {
        byte[] bytes = new byte[32]; // 256 bits, the HS256 minimum
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
        // 1ms expiration - the token is already expired by the time we parse it.
        JwtService service = newService(1L, ONE_HOUR_MS);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String token = service.generateToken(userDetails);
        Thread.sleep(25); // comfortably past the 1ms expiration

        // NOT "returns false" - see the class-level doc comment for why.
        assertThatThrownBy(() -> service.isTokenValid(token, userDetails))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void constructorParameters_accessAndResetExpirationAreNotTransposed() throws InterruptedException {
        // A long access-token life, a near-instant reset-token life. If the
        // two constructor arguments were ever swapped, this would invert:
        // the access token would expire and the reset token would survive.
        JwtService service = newService(ONE_HOUR_MS, 1L);
        when(userDetails.getUsername()).thenReturn("alice@example.com");

        String accessToken = service.generateToken(userDetails);
        String resetToken = service.generatePasswordResetToken(userDetails);

        Thread.sleep(25);

        assertThat(service.extractUsername(accessToken)).isEqualTo("alice@example.com");
        assertThatThrownBy(() -> service.extractUsername(resetToken)).isInstanceOf(JwtException.class);
    }
}
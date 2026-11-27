package com.notare.integration.support;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Test stand-in used by the integration tests. */
@Component
@Profile("test")
public class StubJwtDecoder implements JwtDecoder {

    private final StubInternalAuthClient stubAuthClient;

    public StubJwtDecoder(StubInternalAuthClient stubAuthClient) {
        this.stubAuthClient = stubAuthClient;
    }

    @Override
    public Jwt decode(String token) {
        String username = stubAuthClient.usernameForActiveToken(token);
        if (username == null) {
            throw new BadJwtException("Unknown or revoked token");
        }
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(username)
                .claim("username", username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();
    }
}

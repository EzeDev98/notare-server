package com.notare.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies access tokens minted by internal-auth-server. Notare never issues
 * or stores login sessions itself: IAM is the only session authority.
 * The JWKS is fetched lazily, so notare still boots when IAM is down.
 * Tests supply their own JwtDecoder (profile "test").
 */
@Configuration
@Profile("!test")
public class InternalAuthJwtConfig {

    @Bean
    public JwtDecoder internalAuthJwtDecoder(@Value("${internal-auth.issuer-uri}") String issuerUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuerUri + "/oauth2/jwks").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }
}

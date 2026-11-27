package com.notare.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for WebSecurityConfig. */
public class WebSecurityConfigTest {

    private final WebSecurityConfig config = new WebSecurityConfig(null, null, null);

    @org.junit.jupiter.api.BeforeEach
    void injectValueFields() {
        org.springframework.test.util.ReflectionTestUtils.setField(config, "allowedOriginPatterns",
                new String[]{"https://*.onrender.com", "http://localhost:*"});
    }

    private CorsConfiguration corsConfigurationFor(String path) {
        CorsConfigurationSource source = config.corsConfigurationSource();
        return source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", path));
    }

    @Test
    void corsConfigurationSource_isRegisteredForEveryPath() {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();

        assertThat(source.getCorsConfigurations()).containsKey("/**");
    }

    @Test
    void corsConfigurationSource_allowsTheConfiguredOriginPatterns() {
        CorsConfiguration configuration = corsConfigurationFor("/api/v1/notes");

        assertThat(configuration.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder("https://*.onrender.com", "http://localhost:*");
    }

    @Test
    void corsConfigurationSource_allowsTheExpectedHttpMethods() {
        CorsConfiguration configuration = corsConfigurationFor("/api/v1/notes");

        assertThat(configuration.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH");
    }

    @Test
    void corsConfigurationSource_exposesOnlyContentDispositionAndLocation() {
        CorsConfiguration configuration = corsConfigurationFor("/api/v1/notes");

        assertThat(configuration.getExposedHeaders())
                .containsExactlyInAnyOrder("Content-Disposition", "Location", "Retry-After");
    }

    @Test
    void corsConfigurationSource_allowsCredentials() {
        CorsConfiguration configuration = corsConfigurationFor("/api/v1/notes");

        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void corsConfigurationSource_setsA3600SecondMaxAge() {
        CorsConfiguration configuration = corsConfigurationFor("/api/v1/notes");

        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }
}

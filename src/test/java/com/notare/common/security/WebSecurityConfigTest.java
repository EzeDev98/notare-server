package com.notare.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4f of the coverage roadmap.
 *
 * <p><b>Scope note - read before extending this class.</b> {@link
 * WebSecurityConfig#corsConfigurationSource()} is a plain method that builds
 * and returns a {@link CorsConfigurationSource}, so it can be called directly
 * without booting Spring - that's all this test file covers. The rest of
 * what Step 4f's roadmap entry describes - "each endpoint sits behind the
 * auth rule it's supposed to," the {@code maliciousProbesMatcher()} blocklist,
 * whether {@code /error} and {@code /actuator/health} are really reachable
 * unauthenticated - genuinely needs the real {@code SecurityFilterChain}
 * wired up and HTTP requests fired through it. {@code
 * maliciousProbesMatcher()} is private with no public accessor, and the
 * authorization rules only exist inside the {@code HttpSecurity} DSL inside
 * {@code securityFilterChain()}; neither is reachable by calling a method
 * directly the way {@code corsConfigurationSource()} is. That part belongs to
 * a {@code @WebMvcTest}/{@code @SpringBootTest} + {@code MockMvc} slice test
 * hitting real routes - the same infrastructure Step 15's controller tests
 * already need - rather than a hand-rolled unit test here. Flagging this
 * explicitly rather than silently only covering half of 4f.
 *
 * <p><b>Update (Step 5 of the test-suite fix plan):</b> that slice test now
 * exists - see {@link WebSecurityFilterChainTest}. This class's scope stays
 * exactly as described above; it isn't being extended to cover the DSL.
 *
 * <p>The four no-op constructor arguments below are safe: none of {@code
 * jwtAuthenticationFilter}, {@code userDetailsService}, {@code
 * authenticationEntryPoint}, or {@code accessDeniedHandler} is read by
 * {@code corsConfigurationSource()}.
 */
public class WebSecurityConfigTest {

    private final WebSecurityConfig config = new WebSecurityConfig(null, null, null, null);

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
        // Deliberately narrow - see the bean's own doc comment on why
        // X-Total-Count is NOT here (nothing in this app ever sets it).
        CorsConfiguration configuration = corsConfigurationFor("/api/v1/notes");

        assertThat(configuration.getExposedHeaders())
                .containsExactlyInAnyOrder("Content-Disposition", "Location");
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
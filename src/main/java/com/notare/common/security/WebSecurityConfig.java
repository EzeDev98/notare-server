package com.notare.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailAccessDeniedHandler accessDeniedHandler;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(maliciousProbesMatcher()).denyAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/login/**", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/activation/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/user/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/password/**").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        // IAM -> notare user lifecycle push; gated on a shared secret in UserEventController
                        .requestMatchers(HttpMethod.POST, "/api/v1/internal/user-events").permitAll()
                        // Operator-only wipe endpoint — gated on its own shared
                        // secret inside AdminController, not a user JWT, so it
                        // has to be reachable here without one. See that
                        // controller's own doc comment for why.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").permitAll()
                        // The client's own keep-alive ping (see keepAlive.ts) —
                        // needs to keep working with no session at all, since
                        // its whole point is stopping Render from spinning the
                        // instance down while nobody happens to be logged in.
                        .requestMatchers(HttpMethod.GET, "/api/v1/keep-alive").permitAll()
                        // The collaboration socket handshake only. A browser cannot send an
                        // Authorization header on a WebSocket, so the one-time ticket in the query
                        // string is checked by CollabHandshakeInterceptor instead (step 15c).
                        .requestMatchers(HttpMethod.GET, "/api/v1/ws/collab").permitAll()
                        // Spring Boot forwards any unhandled 404/500 to /error, and that
                        // forward goes through this filter chain as an ERROR dispatch. If
                        // /error isn't permitted it falls to anyRequest().authenticated(),
                        // gets denied, and — since no formLogin/httpBasic is configured,
                        // making Http403ForbiddenEntryPoint the default — comes back as a
                        // 403. The real 404 or 500 is never seen. Every "mysterious 403 on
                        // a path I definitely permitted" in a stateless API traces back to
                        // this line being missing.
                        .requestMatchers("/error").permitAll()
                        // Render's health check hits this path unauthenticated. Only
                        // /actuator/health itself is exposed — no other /actuator/**
                        // endpoint is permitted, so nothing else in Actuator (env,
                        // beans, etc.) is reachable without a token.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/configuration/**",
                                "/webjars/**",
                                "/api-docs",
                                "/api-docs/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                // WITHOUT THIS BLOCK the chain falls back to
                // Http403ForbiddenEntryPoint, and an expired access token comes
                // back as an EMPTY 403 rather than a 401 — indistinguishable
                // from a genuine permission denial, and impossible for a client
                // to build a refresh loop on. See
                // ProblemDetailAuthenticationEntryPoint for the full reasoning.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private RequestMatcher maliciousProbesMatcher() {
        List<String> blockedExtensions = List.of(".php", ".asp", ".aspx", ".env", ".cgi", ".sh", ".bat");
        return request -> {
            String path = request.getRequestURI().toLowerCase();
            return blockedExtensions.stream().anyMatch(path::endsWith);
        };
    }

    /** Step 19c: one list for REST (CORS) and the WebSocket handshake (WebSocketConfig reads the same setting). */
    @org.springframework.beans.factory.annotation.Value(
            "${app.cors.allowed-origin-patterns:https://*.onrender.com,http://localhost:*}")
    private String[] allowedOriginPatterns;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of(allowedOriginPatterns));

        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        ));

        configuration.setAllowedHeaders(List.of("*"));

        // Headers a BROWSER client is allowed to read. Anything not listed here
        // is present on the wire and invisible to fetch() — silently, with no
        // error, which is why this list is easy to get wrong and hard to debug.
        //
        // Location matters: every 201 in this API sets it (notes,
        // collaborations, shares) and so does the 202 on transcription submit.
        // Each of those also returns the resource in the body, so nothing is
        // broken without it — but a client following the Location header on
        // transcription submit, which is the documented way to poll, could not
        // read the URL it was told to poll.
        //
        // X-Total-Count is set by nothing in this application; pagination
        // totals travel inside PagedResponse. Dropped rather than left as a
        // header that promises something the server never sends.
        configuration.setExposedHeaders(List.of(
                "Content-Disposition",
                "Location",
                "Retry-After"
        ));

        // Retained, though nothing currently needs it: this API authenticates
        // with a Bearer header, not cookies, and credentialed CORS is what
        // makes the wildcard origin illegal (hence allowedOriginPatterns above
        // rather than allowedOrigins). It becomes load-bearing only if the
        // refresh token ever moves to an httpOnly cookie.
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

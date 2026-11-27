package com.notare.common.security;

import com.notare.feature.user.service.impl.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Authenticates a request from its internal-auth-server access token. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USERNAME_CLAIM = "username";
    private static final String CLIENT_APPLICATION_ID_CLAIM = "client_application_id";

    private final JwtDecoder jwtDecoder;
    private final CustomUserDetailsService userDetailsService;
    private final String expectedClientApplicationId;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder,
                                   CustomUserDetailsService userDetailsService,
                                   @Value("${internal-auth.client-application-id:}") String expectedClientApplicationId) {
        this.jwtDecoder = jwtDecoder;
        this.userDetailsService = userDetailsService;
        this.expectedClientApplicationId = expectedClientApplicationId == null ? "" : expectedClientApplicationId.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String username = jwt.getClaimAsString(USERNAME_CLAIM);

            if (username != null && belongsToThisApp(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private boolean belongsToThisApp(Jwt jwt) {
        if (expectedClientApplicationId.isEmpty()) {
            return true;
        }
        Object claim = jwt.getClaim(CLIENT_APPLICATION_ID_CLAIM);
        return claim != null && expectedClientApplicationId.equals(String.valueOf(claim));
    }
}

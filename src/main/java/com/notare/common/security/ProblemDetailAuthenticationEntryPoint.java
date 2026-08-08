package com.notare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * What the API answers when a caller is NOT authenticated: 401, as
 * ProblemDetail.
 *
 * <h2>Why this class has to exist</h2>
 *
 * <p>Spring Security's {@code ExceptionTranslationFilter} needs an
 * {@code AuthenticationEntryPoint} to decide what an unauthenticated caller
 * gets. When no {@code formLogin} or {@code httpBasic} is configured — correct
 * for a stateless JWT API — the default it falls back to is
 * {@link org.springframework.security.web.authentication.Http403ForbiddenEntryPoint},
 * which sends <b>403 with an empty body</b>.
 *
 * <p>So before this class, every one of these produced an empty 403:
 *
 * <ul>
 *   <li>no {@code Authorization} header at all</li>
 *   <li>a malformed token</li>
 *   <li><b>an expired access token</b></li>
 * </ul>
 *
 * <p>That third case is the damaging one. {@link JwtAuthenticationFilter}
 * catches the {@code JwtException}, clears the context and continues, so an
 * expired token is indistinguishable from no token by the time authorization
 * runs — and both came back 403.
 *
 * <p>A client cannot build a token-refresh loop on that. The universal
 * convention, and the one any HTTP library assumes, is <b>401 means your
 * credentials are missing or stale, try refreshing; 403 means you are known
 * and still not allowed, do not retry.</b> With 403 for both, a client either
 * never refreshes (and logs the user out every fifteen minutes) or refreshes on
 * every 403 (and hammers {@code /auth/refresh} whenever a READ collaborator
 * touches a note they may not edit — which on this server rotates the refresh
 * token and, on the second concurrent attempt, revokes the whole session).
 *
 * <p>This is the same distinction the application already draws deliberately
 * between 404 and 403 for resources. It just was never drawn for
 * authentication, because the default was silent.
 *
 * <h2>Why the ObjectMapper is injected rather than created</h2>
 *
 * <p>Boot's auto-configured mapper has {@code ProblemDetailJacksonMixin}
 * registered, which is what puts extension members at the JSON root per
 * RFC 9457. A {@code new ObjectMapper()} here would serialise a
 * differently-shaped body from every other error in the application — the same
 * trap as declaring an {@code ObjectMapper} bean, arriving from the other
 * direction.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required. Your session may have expired.");
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));

        // Deliberately vague about WHY. "Token expired" versus "token malformed"
        // versus "no token" is useful to an attacker probing the auth surface and
        // useless to a legitimate client, which does the same thing in all three
        // cases: refresh, then retry once.
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
package com.notare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * What the API answers when a caller IS authenticated but the filter chain
 * still refuses the request: 403, as ProblemDetail.
 *
 * <p>The counterpart to {@link ProblemDetailAuthenticationEntryPoint}, and the
 * split between the two is the whole point. Spring Security decides which one
 * to invoke by asking whether the current authentication is anonymous:
 *
 * <ul>
 *   <li>anonymous or absent → entry point → <b>401</b>, "refresh and retry"</li>
 *   <li>authenticated but denied → this handler → <b>403</b>, "do not retry"</li>
 * </ul>
 *
 * <p>Without an explicit handler, the default writes 403 with an empty body,
 * which is not wrong — but it is the only response in the application that a
 * client cannot parse with the same code as every other error. One shape for
 * everything is worth eleven lines.
 *
 * <p>This is a FILTER-CHAIN denial and is distinct from
 * {@code InsufficientNotePermissionException}, which is the domain's own 403
 * (a READ collaborator attempting a write) and still comes from
 * {@code GlobalExceptionHandler} with a specific, useful message. Nothing in
 * the current security config reaches this handler, since the chain only
 * distinguishes authenticated from not — it exists so that the day a role or
 * scope check is added, the denial does not silently become the one unparseable
 * response.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        problem.setTitle("Forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
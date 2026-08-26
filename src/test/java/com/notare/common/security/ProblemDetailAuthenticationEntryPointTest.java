package com.notare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 4e of the coverage roadmap.
 *
 * <p>Covers {@link ProblemDetailAuthenticationEntryPoint#commence}: the 401
 * status and {@code application/problem+json} content type this class exists
 * specifically to produce instead of Spring's default empty-body 403 (see
 * the class's own extensive doc comment on why that distinction matters for
 * a client's refresh-token loop), the fixed {@code instance} URI, and - the
 * most important behavioral test here - that the response body is
 * IDENTICAL no matter what actually went wrong underneath. The class doc
 * comment is explicit that the vagueness is deliberate ("useful to an
 * attacker probing the auth surface and useless to a legitimate client"), so
 * {@link #commence_producesTheSameBodyRegardlessOfTheUnderlyingAuthenticationExceptionType()}
 * verifies that design goal directly rather than trusting the comment.
 */
@ExtendWith(MockitoExtension.class)
class ProblemDetailAuthenticationEntryPointTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private ServletOutputStream outputStream;

    private ProblemDetailAuthenticationEntryPoint entryPoint;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        entryPoint = new ProblemDetailAuthenticationEntryPoint(objectMapper);
        when(response.getOutputStream()).thenReturn(outputStream);
    }

    @Test
    void commence_setsStatus401AndProblemJsonContentType() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/notes");

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(response).setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        verify(response).setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    @Test
    void commence_writesAProblemDetailWithTheFixedTitleAndMessage() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/notes");

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
        verify(objectMapper).writeValue(eq(outputStream), captor.capture());

        ProblemDetail problem = captor.getValue();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getTitle()).isEqualTo("Unauthorized");
        assertThat(problem.getDetail()).isEqualTo("Authentication is required. Your session may have expired.");
    }

    @Test
    void commence_setsInstanceToTheRequestUri() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/refresh");

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
        verify(objectMapper).writeValue(eq(outputStream), captor.capture());

        assertThat(captor.getValue().getInstance()).isEqualTo(URI.create("/api/v1/auth/refresh"));
    }

    @Test
    void commence_producesTheSameBodyRegardlessOfTheUnderlyingAuthenticationExceptionType() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/notes");

        entryPoint.commence(request, response, new BadCredentialsException("wrong password"));
        ArgumentCaptor<ProblemDetail> firstCaptor = ArgumentCaptor.forClass(ProblemDetail.class);
        verify(objectMapper).writeValue(eq(outputStream), firstCaptor.capture());

        org.mockito.Mockito.reset(objectMapper, response);
        when(response.getOutputStream()).thenReturn(outputStream);

        entryPoint.commence(request, response, new CredentialsExpiredException("token expired"));
        ArgumentCaptor<ProblemDetail> secondCaptor = ArgumentCaptor.forClass(ProblemDetail.class);
        verify(objectMapper).writeValue(eq(outputStream), secondCaptor.capture());

        // Different underlying causes, byte-for-byte identical detail text -
        // an attacker probing the auth surface (or a legitimate client
        // trying to distinguish "expired" from "malformed" from "missing")
        // learns nothing from the message either way.
        assertThat(firstCaptor.getValue().getDetail()).isEqualTo(secondCaptor.getValue().getDetail());
        assertThat(firstCaptor.getValue().getDetail())
                .doesNotContain("wrong password")
                .doesNotContain("token expired");
    }
}
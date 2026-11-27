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

/** Tests for ProblemDetailAuthenticationEntryPoint. */
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

        assertThat(firstCaptor.getValue().getDetail()).isEqualTo(secondCaptor.getValue().getDetail());
        assertThat(firstCaptor.getValue().getDetail())
                .doesNotContain("wrong password")
                .doesNotContain("token expired");
    }
}

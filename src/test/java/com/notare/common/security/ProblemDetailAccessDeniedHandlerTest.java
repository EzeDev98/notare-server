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
import org.springframework.security.access.AccessDeniedException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 4d of the coverage roadmap.
 *
 * <p>Covers {@link ProblemDetailAccessDeniedHandler#handle}: the 403 status
 * and {@code application/problem+json} content type, the fixed
 * title/detail text (deliberately generic - see
 * {@link #handle_neverLeaksTheUnderlyingExceptionMessageIntoTheResponse()}),
 * and that {@code instance} is populated from the request's own URI so a
 * client can tell which call was denied.
 *
 * <p>{@code ObjectMapper} is mocked rather than real - the class's own doc
 * comment explains why a real {@code new ObjectMapper()} would be WRONG here
 * (it wouldn't carry Boot's {@code ProblemDetailJacksonMixin} and would
 * serialize a different shape than every other error in the app), so this
 * test only verifies the class hands the mapper the right {@link
 * ProblemDetail}, not how Jackson itself serializes it.
 */
@ExtendWith(MockitoExtension.class)
class ProblemDetailAccessDeniedHandlerTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private ServletOutputStream outputStream;

    private ProblemDetailAccessDeniedHandler handler;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        handler = new ProblemDetailAccessDeniedHandler(objectMapper);
        when(response.getOutputStream()).thenReturn(outputStream);
    }

    @Test
    void handle_setsStatus403AndProblemJsonContentType() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/notes/5");

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
        verify(response).setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        verify(response).setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    @Test
    void handle_writesAProblemDetailWithTheFixedTitleAndMessage() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/notes/5");

        handler.handle(request, response, new AccessDeniedException("denied"));

        ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
        verify(objectMapper).writeValue(org.mockito.ArgumentMatchers.eq(outputStream), captor.capture());

        ProblemDetail problem = captor.getValue();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Forbidden");
        assertThat(problem.getDetail()).isEqualTo("You do not have permission to perform this action.");
    }

    @Test
    void handle_setsInstanceToTheRequestUri() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/voice-notes/9/shares");

        handler.handle(request, response, new AccessDeniedException("denied"));

        ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
        verify(objectMapper).writeValue(org.mockito.ArgumentMatchers.eq(outputStream), captor.capture());

        assertThat(captor.getValue().getInstance()).isEqualTo(URI.create("/api/v1/voice-notes/9/shares"));
    }

    @Test
    void handle_neverLeaksTheUnderlyingExceptionMessageIntoTheResponse() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/notes/5");

        handler.handle(request, response,
                new AccessDeniedException("internal detail: user 7 lacks ROLE_ADMIN on resource 5"));

        ArgumentCaptor<ProblemDetail> captor = ArgumentCaptor.forClass(ProblemDetail.class);
        verify(objectMapper).writeValue(org.mockito.ArgumentMatchers.eq(outputStream), captor.capture());

        assertThat(captor.getValue().getDetail())
                .isEqualTo("You do not have permission to perform this action.")
                .doesNotContain("ROLE_ADMIN");
    }
}
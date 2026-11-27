package com.notare.common.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Tests for GlobalExceptionHandler. */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void classIsAnnotatedAsARestControllerAdvice() {
        assertThat(GlobalExceptionHandler.class.isAnnotationPresent(RestControllerAdvice.class)).isTrue();
    }

    @Test
    void classDeclaresHighestPrecedenceOrder() {
        Order order = GlobalExceptionHandler.class.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Mock
    private MethodParameter methodParameter;

    @Mock
    private BindingResult bindingResult;

    private MethodArgumentNotValidException manve() {
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }

    @Test
    void handleMethodArgumentNotValid_singleFieldError_mapsToErrorsExtension() {
        when(bindingResult.getFieldErrors()).thenReturn(
                List.of(new FieldError("registrationRequest", "username", "must not be blank")));
        when(bindingResult.getGlobalErrors()).thenReturn(List.of());

        ProblemDetail problem = handler.handleMethodArgumentNotValid(manve());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Validation Failed");
        assertThat(problem.getDetail()).isEqualTo("One or more fields are invalid.");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly(Map.entry("username", "must not be blank"));
    }

    @Test
    void handleMethodArgumentNotValid_sameFieldFailingTwice_mergesBothMessages() {
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("resetPasswordRequest", "password", "must be at least 8 characters"),
                new FieldError("resetPasswordRequest", "password", "must contain a digit")));
        when(bindingResult.getGlobalErrors()).thenReturn(List.of());

        ProblemDetail problem = handler.handleMethodArgumentNotValid(manve());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).hasSize(1);
        assertThat(errors.get("password"))
                .isEqualTo("must be at least 8 characters must contain a digit");
    }

    @Test
    void handleMethodArgumentNotValid_globalError_isFiledUnderTheObjectName() {
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        when(bindingResult.getGlobalErrors()).thenReturn(
                List.of(new ObjectError("resetPasswordRequest", "passwords must match")));

        ProblemDetail problem = handler.handleMethodArgumentNotValid(manve());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly(Map.entry("resetPasswordRequest", "passwords must match"));
    }

    @Test
    void handleMethodArgumentNotValid_fieldAndGlobalErrorsTogether_bothSurvive() {
        when(bindingResult.getFieldErrors()).thenReturn(
                List.of(new FieldError("resetPasswordRequest", "email", "must be a well-formed email address")));
        when(bindingResult.getGlobalErrors()).thenReturn(
                List.of(new ObjectError("resetPasswordRequest", "passwords must match")));

        ProblemDetail problem = handler.handleMethodArgumentNotValid(manve());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).containsExactlyInAnyOrderEntriesOf(Map.of(
                "email", "must be a well-formed email address",
                "resetPasswordRequest", "passwords must match"));
    }

    @Test
    void handleMethodArgumentNotValid_missingDefaultMessage_fallsBackToInvalidValue() {
        when(bindingResult.getFieldErrors()).thenReturn(
                List.of(new FieldError("createNoteRequest", "content", null)));
        when(bindingResult.getGlobalErrors()).thenReturn(List.of());

        ProblemDetail problem = handler.handleMethodArgumentNotValid(manve());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors.get("content")).isEqualTo("Invalid value.");
    }

    @Test
    void handleMethodArgumentNotValid_blankDefaultMessage_alsoFallsBackToInvalidValue() {
        when(bindingResult.getFieldErrors()).thenReturn(
                List.of(new FieldError("createNoteRequest", "content", "   ")));
        when(bindingResult.getGlobalErrors()).thenReturn(List.of());

        ProblemDetail problem = handler.handleMethodArgumentNotValid(manve());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors.get("content")).isEqualTo("Invalid value.");
    }

    @Test
    void handleMethodArgumentNotValid_noErrorsAtAll_returnsEmptyErrorsMap() {
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        when(bindingResult.getGlobalErrors()).thenReturn(List.of());

        ProblemDetail problem = handler.handleMethodArgumentNotValid(manve());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).isEmpty();
    }

    @Test
    void handleBadCredentialsException_returns401WithFixedMessage() {
        ProblemDetail problem = handler.handleBadCredentialsException(
                new BadCredentialsException("bad creds"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("Invalid username/email or password.");
    }

    @Test
    void handleDisabledException_returns403PromptingEmailVerification() {
        ProblemDetail problem = handler.handleDisabledException(new DisabledException("disabled"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getDetail()).isEqualTo("Please verify your email before logging in.");
    }

    @Test
    void handleLockedException_returns423WithSupportMessage() {
        ProblemDetail problem = handler.handleLockedException(new LockedException("locked"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.LOCKED.value());
        assertThat(problem.getDetail()).isEqualTo("This account has been locked. Please contact support.");
    }

    @Test
    void handleAuthenticationException_genericCase_returns401WithGenericMessage() {
        AuthenticationException generic = new AuthenticationException("some auth failure") {};

        ProblemDetail problem = handler.handleAuthenticationException(generic);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("Authentication failed.");
    }

    @Test
    void handlePasswordMisMatchException_passesMessageThrough() {
        ProblemDetail problem = handler.handlePasswordMisMatchException(
                new PasswordMisMatchException("Passwords do not match."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Passwords do not match.");
    }

    @Test
    void handleInvalidOtpException_passesMessageThrough() {
        ProblemDetail problem = handler.handleInvalidOtpException(
                new InvalidOtpException("The code you entered is incorrect."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("The code you entered is incorrect.");
    }

    @Test
    void handleOtpExpiredException_passesMessageThrough() {
        ProblemDetail problem = handler.handleOtpExpiredException(
                new OtpExpiredException("This code has expired."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("This code has expired.");
    }

    @Test
    void handleInvalidVoiceUploadException_passesMessageThrough() {
        ProblemDetail problem = handler.handleInvalidVoiceUploadException(
                new InvalidVoiceUploadException("The uploaded file is empty."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("The uploaded file is empty.");
    }

    @Test
    void handleUserNotFound_returns404WithMessage() {
        ProblemDetail problem = handler.handleUserNotFound(new UserNotFoundException("User not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("User not found.");
    }

    @Test
    void handleNoteNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleNoteNotFoundException(new NoteNotFoundException("Note not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Note not found.");
    }

    @Test
    void handleVoiceNoteNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleVoiceNoteNotFoundException(
                new VoiceNoteNotFoundException("Voice note not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Voice note not found.");
    }

    @Test
    void handleTranscriptionNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleTranscriptionNotFoundException(
                new TranscriptionNotFoundException("Transcription not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Transcription not found.");
    }

    @Test
    void handleNotificationNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleNotificationNotFoundException(
                new NotificationNotFoundException("Notification not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Notification not found.");
    }

    @Test
    void handleSessionNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleSessionNotFoundException(
                new SessionNotFoundException("Session not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Session not found.");
    }

    @Test
    void handleUserAlreadyExistException_returns409WithMessage() {
        ProblemDetail problem = handler.handleUserAlreadyExistException(
                new UserAlreadyExistException("A user with this email already exists."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("A user with this email already exists.");
    }

    @Test
    void handleOptimisticLockingFailure_returns409WithFixedMessage() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Note", 5L);

        ProblemDetail problem = handler.handleOptimisticLockingFailure(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail())
                .isEqualTo("This note was modified by another request. Please reload and try again.");
    }

    @Test
    void handleVoiceFileTooLargeException_returns413WithMessage() {
        ProblemDetail problem = handler.handleVoiceFileTooLargeException(
                new VoiceFileTooLargeException("The file exceeds the maximum allowed size."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getDetail()).isEqualTo("The file exceeds the maximum allowed size.");
    }

    @Test
    void handleMaxUploadSizeExceededException_returns413WithFixedMessage() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L);

        ProblemDetail problem = handler.handleMaxUploadSizeExceededException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getDetail()).isEqualTo("Upload exceeds the maximum allowed size.");
    }

    @Test
    void handleNotarianIdGenerationException_returns500WithMessage() {
        ProblemDetail problem = handler.handleNotarianIdGenerationException(
                new NotarianIdGenerationException("Unable to generate a unique notarian id."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("Unable to generate a unique notarian id.");
    }

    @Test
    void handleOtpResendCooldownException_returns429WithMessage() {
        ProblemDetail problem = handler.handleOtpResendCooldownException(
                new OtpResendCooldownException("Please wait before requesting another code."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(problem.getDetail()).isEqualTo("Please wait before requesting another code.");
    }

    @Test
    void handleOtpAttemptsExceededException_returns429WithMessage() {
        ProblemDetail problem = handler.handleOtpAttemptsExceededException(
                new OtpAttemptsExceededException("Too many incorrect attempts."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(problem.getDetail()).isEqualTo("Too many incorrect attempts.");
    }

    @Test
    void handleUnsupportedAudioFormatException_returns400WithMessage() {
        ProblemDetail problem = handler.handleUnsupportedAudioFormatException(
                new UnsupportedAudioFormatException("Only audio/webm and audio/mp4 are supported."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Only audio/webm and audio/mp4 are supported.");
    }

    @Test
    void handleStorageException_returns500WithFixedMessageNotTheRawExceptionMessage() {
        StorageException ex = new StorageException("Failed to write to /var/notare/uploads/abc123");

        ProblemDetail problem = handler.handleStorageException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("A storage error occurred. Please try again.");
        assertThat(problem.getDetail()).doesNotContain("/var/notare/uploads/abc123");
    }

    @Test
    void handleEmailDeliveryException_returns503WithMessage() {
        ProblemDetail problem = handler.handleEmailDeliveryException(
                new EmailDeliveryException("The email provider could not be reached."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getDetail()).isEqualTo("The email provider could not be reached.");
    }

    @Test
    void handleInvalidResetTokenException_returns401WithMessage() {
        ProblemDetail problem = handler.handleInvalidResetTokenException(
                new InvalidResetTokenException("This reset link is invalid or has expired."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("This reset link is invalid or has expired.");
    }

    @Test
    void handleInvalidRefreshTokenException_returns401WithMessage() {
        ProblemDetail problem = handler.handleInvalidRefreshTokenException(new InvalidRefreshTokenException("This refresh token is invalid or has expired."));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("This refresh token is invalid or has expired.");
    }
}

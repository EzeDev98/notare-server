package com.notare.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Turns every error into an RFC 9457 ProblemDetail response. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.merge(
                    fieldError.getField(),
                    messageOf(fieldError),
                    (existing, addition) -> existing + " " + addition);
        }

        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            errors.merge(
                    globalError.getObjectName(),
                    messageOf(globalError),
                    (existing, addition) -> existing + " " + addition);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private String messageOf(ObjectError error) {
        String message = error.getDefaultMessage();
        return (message == null || message.isBlank()) ? "Invalid value." : message;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentialsException(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username/email or password.");
    }

    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleDisabledException(DisabledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Please verify your email before logging in.");
    }

    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLockedException(LockedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, "This account has been locked. Please contact support.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication failed.");
    }

    @ExceptionHandler(PasswordMisMatchException.class)
    public ProblemDetail handlePasswordMisMatchException(PasswordMisMatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistException.class)
    public ProblemDetail handleUserAlreadyExistException(UserAlreadyExistException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RegistrationException.class)
    public ProblemDetail handleRegistrationException(RegistrationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InternalAuthCallException.class)
    public ProblemDetail handleInternalAuthCallException(InternalAuthCallException ex) {
        log.warn("internal-auth-server call failed: status={}, message={}", ex.getStatus().value(), ex.getMessage());
        if (ex.getStatus() == HttpStatus.FORBIDDEN
                && ex.getMessage() != null
                && ex.getMessage().toLowerCase().contains("disabled")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED,
                    "This account has been disabled. Please contact support.");
        }
        if (ex.getStatus().is4xxClientError()) {
            return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Authentication service error. Please try again later.");
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail handleResourceAccessException(ResourceAccessException ex) {
        log.error("internal-auth-server unreachable: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service is unavailable. Please try again later.");
    }

    @ExceptionHandler(NotarianIdGenerationException.class)
    public ProblemDetail handleNotarianIdGenerationException(NotarianIdGenerationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(OtpResendCooldownException.class)
    public ProblemDetail handleOtpResendCooldownException(OtpResendCooldownException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ProblemDetail handleInvalidOtpException(InvalidOtpException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ProblemDetail handleOtpExpiredException(OtpExpiredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(OtpAttemptsExceededException.class)
    public ProblemDetail handleOtpAttemptsExceededException(OtpAttemptsExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoteNotFoundException.class)
    public ProblemDetail handleNoteNotFoundException(NoteNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(VoiceNoteNotFoundException.class)
    public ProblemDetail handleVoiceNoteNotFoundException(VoiceNoteNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TranscriptionNotFoundException.class)
    public ProblemDetail handleTranscriptionNotFoundException(TranscriptionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoteConflictException.class)
    public ProblemDetail handleNoteConflict(NoteConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This note has been modified since you loaded it.");
        problem.setTitle("Conflict");
        problem.setProperty("code", "STALE_VERSION");
        problem.setProperty("currentVersion", ex.getCurrentVersion());
        return problem;
    }

    @ExceptionHandler(InvalidAccountRequestException.class)
    public ProblemDetail handleInvalidAccountRequest(InvalidAccountRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This note was modified by another request. Please reload and try again.");
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleNotificationNotFoundException(NotificationNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UnsupportedAudioFormatException.class)
    public ProblemDetail handleUnsupportedAudioFormatException(UnsupportedAudioFormatException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(VoiceFileTooLargeException.class)
    public ProblemDetail handleVoiceFileTooLargeException(VoiceFileTooLargeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
    }

    @ExceptionHandler(InvalidVoiceUploadException.class)
    public ProblemDetail handleInvalidVoiceUploadException(InvalidVoiceUploadException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "Upload exceeds the maximum allowed size.");
    }

    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorageException(StorageException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "A storage error occurred. Please try again.");
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ProblemDetail handleEmailDeliveryException(EmailDeliveryException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ProblemDetail handleInvalidResetTokenException(InvalidResetTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail handleSessionNotFoundException(SessionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequestsException(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(ClientTooOldException.class)
    public ProblemDetail handleClientTooOldException(ClientTooOldException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UPGRADE_REQUIRED, ex.getMessage());
        problem.setTitle("Update required");
        problem.setProperty("code", "CLIENT_TOO_OLD");
        problem.setProperty("minimumVersion", ex.getMinimumVersion());
        return problem;
    }

}

package com.notare.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(CollaborationNotFoundException.class)
    public ProblemDetail handleCollaborationNotFoundException(CollaborationNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoteConflictException.class)
    public ProblemDetail handleNoteConflictException(NoteConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("currentVersion", ex.getCurrentVersion());
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This note was modified by another request. Please reload and try again.");
    }

    @ExceptionHandler(CollaborationAlreadyExistsException.class)
    public ProblemDetail handleCollaborationAlreadyExistsException(CollaborationAlreadyExistsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCollaborationException.class)
    public ProblemDetail handleInvalidCollaborationException(InvalidCollaborationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NoteShareNotFoundException.class)
    public ProblemDetail handleNoteShareNotFoundException(NoteShareNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoteAlreadySharedException.class)
    public ProblemDetail handleNoteAlreadySharedException(NoteAlreadySharedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidNoteShareException.class)
    public ProblemDetail handleInvalidNoteShareException(InvalidNoteShareException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * The only domain 403 in the application. Everything else that a caller has
     * no business touching is answered with 404, which hides existence; this one
     * fires only after a grant has been found, so the caller already knows the
     * note is there and a 404 would just be confusing. See the exception's own
     * javadoc for the full rule.
     */
    @ExceptionHandler(InsufficientNotePermissionException.class)
    public ProblemDetail handleInsufficientNotePermissionException(InsufficientNotePermissionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UnsupportedAudioFormatException.class)
    public ProblemDetail handleUnsupportedAudioFormatException(UnsupportedAudioFormatException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(VoiceFileTooLargeException.class)
    public ProblemDetail handleVoiceFileTooLargeException(VoiceFileTooLargeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
    }

    @ExceptionHandler(InvalidVoiceUploadException.class)
    public ProblemDetail handleInvalidVoiceUploadException(InvalidVoiceUploadException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Spring throws this when an upload exceeds spring.servlet.multipart.max-file-size
     * (the hard container limit) BEFORE our validator runs. Map it to the same
     * 413 so the client sees a consistent "too large" response either way.
     */
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
}
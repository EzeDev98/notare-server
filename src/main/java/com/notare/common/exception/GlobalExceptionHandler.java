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

/**
 * Every error this application produces through Spring MVC, as RFC 9457
 * ProblemDetail.
 *
 * <h2>Why the explicit @Order</h2>
 *
 * <p>{@code spring.mvc.problemdetails.enabled=true} registers Boot's own
 * {@code ProblemDetailsExceptionHandler}, which converts the framework's
 * exceptions — unreadable body, wrong media type, missing request parameter,
 * unknown path — into ProblemDetail as well. That is exactly what we want for
 * everything this class does NOT declare.
 *
 * <p>It also declares a handler for {@link MethodArgumentNotValidException},
 * and so do we. When two advice beans can both handle an exception, the one
 * with higher precedence wins, and an unordered {@code @RestControllerAdvice}
 * sits at {@code LOWEST_PRECEDENCE} — behind Boot's. Without the annotation
 * below, the validation handler at the bottom of this class would be dead code
 * and every 400 would come back with Boot's generic wording and no field
 * detail, exactly as if we had never written it. Nothing warns you.
 *
 * <p>Being first is safe because this class declares only specific exception
 * types. There is no catch-all {@code Exception} handler here, and there should
 * not be: an unhandled exception must reach the container as a 500 rather than
 * be flattened into a tidy message that hides a bug.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean-validation failure on a {@code @Valid @RequestBody}.
     *
     * <p>Before this handler existed, these did not go through ProblemDetail at
     * all: this advice did not declare the exception and did not extend
     * {@code ResponseEntityExceptionHandler}, so a failed {@code @Valid} fell
     * through to Boot's default error attributes — {@code {timestamp, status,
     * error, path}} — with no per-field information, because
     * {@code server.error.include-binding-errors} is unset.
     *
     * <p>The practical consequence was that <b>no client could show a
     * field-level error on registration or password reset.</b> Every one of the
     * constraints on RegistrationRequest — username length, the password
     * pattern, a malformed email — arrived as an indistinguishable 400. The
     * client's only option was to duplicate all of them and hope the two
     * definitions never drifted.
     *
     * <p>The field map goes in a root-level {@code errors} extension member,
     * per RFC 9457. Keys are field names as the client sent them, so a form can
     * attach each message to its input without a translation table.
     *
     * <p>{@code merge} rather than {@code put}: one field can fail several
     * constraints at once (a password can be too short AND miss a digit), and
     * a plain put would silently keep whichever Hibernate Validator happened to
     * report last.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.merge(
                    fieldError.getField(),
                    messageOf(fieldError),
                    (existing, addition) -> existing + " " + addition);
        }

        // Class-level constraints (a hypothetical @PasswordsMatch on the whole
        // request) produce global errors with no field to attach to. Filed
        // under the object name rather than dropped — an error the user cannot
        // see is worse than one filed slightly awkwardly.
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

    /**
     * A constraint with no message configured would otherwise serialise as
     * null, which a client cannot render.
     */
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

    /**
     * internal-auth-server answered with an error status. 4xx (validation,
     * conflict, rejected credentials) pass through with IAM's own message so the
     * caller sees why. A 5xx from IAM becomes a 502: Notare itself is fine, an
     * upstream dependency failed, and IAM's internals are not the caller's
     * business. The real status and message are always logged.
     */
    @ExceptionHandler(InternalAuthCallException.class)
    public ProblemDetail handleInternalAuthCallException(InternalAuthCallException ex) {
        log.warn("internal-auth-server call failed: status={}, message={}", ex.getStatus().value(), ex.getMessage());
        // IAM answers 403 both for "verify your email" and for an admin-disabled account.
        // Only the first should send the client to the verification page, so the
        // disabled case is surfaced with the same status as a locked account.
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

    /**
     * Connection refused / timeout reaching internal-auth-server. InternalAuthClient
     * is the only RestClient user in this app, so this can only mean IAM is unreachable.
     */
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

    /**
     * A stale version (step 17a). {@code currentVersion} sits at the root of the body, per
     * RFC 9457, so the client can adopt it without a second request. {@code code} lets a client
     * tell this 409 from BODY_IS_COLLABORATIVE without parsing prose.
     */
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
        // Documented in VoiceNoteControllerTest's class Javadoc as 400
        // alongside InvalidVoiceUploadException, not 415: an unsupported
        // audio format is a validation failure on the upload request, the
        // same family as a missing/empty file, not a content-negotiation
        // failure between client and server.
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

    /** Step 17j (B-15): a per-user rate limit was exceeded. Retry-After tells the client when to try again. */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequestsException(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }


    /** Step 17k: the client is older than the configured minimum. The client shows its blocking "Update required" modal. */
    @ExceptionHandler(ClientTooOldException.class)
    public ProblemDetail handleClientTooOldException(ClientTooOldException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UPGRADE_REQUIRED, ex.getMessage());
        problem.setTitle("Update required");
        problem.setProperty("code", "CLIENT_TOO_OLD");
        problem.setProperty("minimumVersion", ex.getMinimumVersion());
        return problem;
    }

}

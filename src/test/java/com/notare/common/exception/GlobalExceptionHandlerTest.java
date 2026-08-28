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

/**
 * Step 14a of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito, Spring Web, and Spring
 * Security are unavailable as compile-time dependencies here (see the note
 * in {@code CustomUserDetailsServiceTest}). Reasoned from source; run via
 * {@code mvn test} before trusting it.
 *
 * <p>{@link GlobalExceptionHandler} has no fields and no constructor
 * dependencies - every {@code @ExceptionHandler} method is a pure function
 * from an exception to a {@link ProblemDetail}. That makes this a plain unit
 * test with no Spring context needed: each handler is called directly with a
 * hand-built exception and the returned status/detail/title/extension
 * properties are asserted, exactly the way MockMvc would exercise it end to
 * end but without the web layer in between.
 *
 * <p>Three things get more than a one-line status/detail check because the
 * class's own javadoc calls them out as easy to get wrong silently:
 * <ul>
 *   <li>{@code handleMethodArgumentNotValid} - the {@code merge} semantics
 *   (same field failing twice, global errors filed under the object name,
 *   the null-message fallback) are exactly the behavior the method's javadoc
 *   says would otherwise regress into "the last constraint wins" or a null
 *   in the JSON body.</li>
 *   <li>{@code handleNoteConflictException} - the one handler that adds a
 *   custom extension member ({@code currentVersion}) beyond title/detail.</li>
 *   <li>the class-level {@code @Order(Ordered.HIGHEST_PRECEDENCE)} - per the
 *   class javadoc, without it Boot's own ProblemDetailsExceptionHandler wins
 *   the tie on {@link MethodArgumentNotValidException} and every 400 loses
 *   its field detail, with nothing at runtime pointing back at this
 *   annotation as the cause. Asserted by reflection so a well-meaning
 *   "cleanup" that deletes the annotation fails a test instead of silently
 *   reintroducing the exact bug the comment describes.</li>
 * </ul>
 *
 * <p>Everything else in the class is "map this exception type to this status
 * with the exception's own message as the detail," so those get one focused
 * test each, grouped by the status family they map to, plus a couple of
 * differential tests (two NOT_FOUND exceptions, two 403s) confirming the
 * status/message pairing isn't accidentally shared or swapped between
 * near-identical handlers.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ----------------------------------------------------------------
    // Class-level wiring: @RestControllerAdvice + @Order(HIGHEST_PRECEDENCE)
    // ----------------------------------------------------------------

    @Test
    void classIsAnnotatedAsARestControllerAdvice() {
        assertThat(GlobalExceptionHandler.class.isAnnotationPresent(RestControllerAdvice.class)).isTrue();
    }

    @Test
    void classDeclaresHighestPrecedenceOrder() {
        // Per the class javadoc: without this, an unordered advice bean sits
        // at LOWEST_PRECEDENCE, Boot's own ProblemDetailsExceptionHandler
        // wins ties on MethodArgumentNotValidException, and the field-level
        // validation handler below becomes dead code with no runtime signal
        // that it happened.
        Order order = GlobalExceptionHandler.class.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    // ----------------------------------------------------------------
    // handleMethodArgumentNotValid
    // ----------------------------------------------------------------

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
        // The exact scenario called out in the handler's javadoc: a password
        // that is both too short AND missing a digit must not silently lose
        // one of the two messages to a plain Map.put.
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
        // A constraint declared with no message() would otherwise serialise
        // as a literal null in the JSON body, which no client can render.
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

    // ----------------------------------------------------------------
    // Authentication-related handlers (all map to non-2xx auth statuses)
    // ----------------------------------------------------------------

    @Test
    void handleBadCredentialsException_returns401WithFixedMessage() {
        ProblemDetail problem = handler.handleBadCredentialsException(
                new BadCredentialsException("bad creds"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        // Fixed wording regardless of the exception's own message, so a
        // brute-forcer can't distinguish "wrong username" from "wrong
        // password" via the response body.
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
        // The catch-all for AuthenticationException subtypes not specifically
        // handled above (BadCredentials/Disabled/Locked each have their own,
        // more specific handler and would be dispatched there instead).
        AuthenticationException generic = new AuthenticationException("some auth failure") {};

        ProblemDetail problem = handler.handleAuthenticationException(generic);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("Authentication failed.");
    }

    // ----------------------------------------------------------------
    // 400 Bad Request handlers (message passed through verbatim)
    // ----------------------------------------------------------------

    @Test
    void handlePasswordMisMatchException_passesMessageThrough() {
        ProblemDetail problem = handler.handlePasswordMisMatchException(
                new PasswordMisMatchException("Passwords do not match."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Passwords do not match.");
    }

    @Test
    void handleInvalidUserLookupException_passesMessageThrough() {
        ProblemDetail problem = handler.handleInvalidUserLookupException(
                new InvalidUserLookupException("At least one id is required."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("At least one id is required.");
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
    void handleInvalidCollaborationException_passesMessageThrough() {
        ProblemDetail problem = handler.handleInvalidCollaborationException(
                new InvalidCollaborationException("You cannot invite yourself."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("You cannot invite yourself.");
    }

    @Test
    void handleInvalidNoteShareException_passesMessageThrough() {
        ProblemDetail problem = handler.handleInvalidNoteShareException(
                new InvalidNoteShareException("Cannot share a note with yourself."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Cannot share a note with yourself.");
    }

    @Test
    void handleInvalidVoiceShareException_passesMessageThrough() {
        ProblemDetail problem = handler.handleInvalidVoiceShareException(
                new InvalidVoiceShareException("Cannot share a voice note with yourself."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Cannot share a voice note with yourself.");
    }

    @Test
    void handleInvalidVoiceUploadException_passesMessageThrough() {
        ProblemDetail problem = handler.handleInvalidVoiceUploadException(
                new InvalidVoiceUploadException("The uploaded file is empty."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("The uploaded file is empty.");
    }

    // ----------------------------------------------------------------
    // 404 Not Found handlers - each entity family gets its own exception
    // type; confirmed independently so no two are accidentally wired to
    // the wrong status or swapped in message handling.
    // ----------------------------------------------------------------

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
    void handleCollaborationNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleCollaborationNotFoundException(
                new CollaborationNotFoundException("Collaboration not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Collaboration not found.");
    }

    @Test
    void handleNoteShareNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleNoteShareNotFoundException(
                new NoteShareNotFoundException("Share not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Share not found.");
    }

    @Test
    void handleVoiceShareNotFoundException_returns404WithMessage() {
        ProblemDetail problem = handler.handleVoiceShareNotFoundException(
                new VoiceShareNotFoundException("Voice share not found."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Voice share not found.");
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

    // ----------------------------------------------------------------
    // 409 Conflict handlers
    // ----------------------------------------------------------------

    @Test
    void handleUserAlreadyExistException_returns409WithMessage() {
        ProblemDetail problem = handler.handleUserAlreadyExistException(
                new UserAlreadyExistException("A user with this email already exists."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("A user with this email already exists.");
    }

    @Test
    void handleCollaborationAlreadyExistsException_returns409WithMessage() {
        ProblemDetail problem = handler.handleCollaborationAlreadyExistsException(
                new CollaborationAlreadyExistsException("Collaboration already exists."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("Collaboration already exists.");
    }

    @Test
    void handleNoteAlreadySharedException_returns409WithMessage() {
        ProblemDetail problem = handler.handleNoteAlreadySharedException(
                new NoteAlreadySharedException("This note is already shared with that user."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("This note is already shared with that user.");
    }

    @Test
    void handleVoiceAlreadySharedException_returns409WithMessage() {
        ProblemDetail problem = handler.handleVoiceAlreadySharedException(
                new VoiceAlreadySharedException("This voice note is already shared with that user."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("This voice note is already shared with that user.");
    }

    @Test
    void handleNoteConflictException_returns409WithMessageAndCurrentVersionExtension() {
        // The one handler with a custom extension member beyond title/detail.
        NoteConflictException ex = new NoteConflictException(
                "This note was modified by another request.", 7L);

        ProblemDetail problem = handler.handleNoteConflictException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("This note was modified by another request.");
        assertThat(problem.getProperties().get("currentVersion")).isEqualTo(7L);
    }

    @Test
    void handleNoteConflictException_currentVersionCanBeNull() {
        // NoteConflictException accepts a null currentVersion (e.g. the note
        // was deleted concurrently); the handler must not NPE unboxing it.
        NoteConflictException ex = new NoteConflictException("Conflict.", null);

        ProblemDetail problem = handler.handleNoteConflictException(ex);

        assertThat(problem.getProperties().get("currentVersion")).isNull();
    }

    @Test
    void handleOptimisticLockingFailure_returns409WithFixedMessage() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Note", 5L);

        ProblemDetail problem = handler.handleOptimisticLockingFailure(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        // Fixed, client-friendly wording rather than the raw JPA exception
        // message, which would leak persistence-layer detail (entity name,
        // identifier) to the client.
        assertThat(problem.getDetail())
                .isEqualTo("This note was modified by another request. Please reload and try again.");
    }

    // ----------------------------------------------------------------
    // 403 Forbidden handlers - the only two domain 403s in the app, per
    // the class's own javadoc on InsufficientNotePermissionException.
    // ----------------------------------------------------------------

    @Test
    void handleInsufficientNotePermissionException_returns403WithMessage() {
        ProblemDetail problem = handler.handleInsufficientNotePermissionException(
                new InsufficientNotePermissionException("You do not have write access to this note."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getDetail()).isEqualTo("You do not have write access to this note.");
    }

    @Test
    void handleInsufficientVoiceNotePermissionException_returns403WithMessage() {
        ProblemDetail problem = handler.handleInsufficientVoiceNotePermissionException(
                new InsufficientVoiceNotePermissionException("You do not have write access to this voice note."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getDetail()).isEqualTo("You do not have write access to this voice note.");
    }

    // ----------------------------------------------------------------
    // 413 Payload Too Large handlers - two different triggers, same status
    // ----------------------------------------------------------------

    @Test
    void handleVoiceFileTooLargeException_returns413WithMessage() {
        ProblemDetail problem = handler.handleVoiceFileTooLargeException(
                new VoiceFileTooLargeException("The file exceeds the maximum allowed size."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getDetail()).isEqualTo("The file exceeds the maximum allowed size.");
    }

    @Test
    void handleMaxUploadSizeExceededException_returns413WithFixedMessage() {
        // Thrown by the servlet container BEFORE VoiceUploadValidator ever
        // runs, so it carries none of the application's own wording - the
        // handler supplies a fixed message so the client sees a consistent
        // "too large" response either way.
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L);

        ProblemDetail problem = handler.handleMaxUploadSizeExceededException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getDetail()).isEqualTo("Upload exceeds the maximum allowed size.");
    }

    // ----------------------------------------------------------------
    // Remaining single-purpose handlers
    // ----------------------------------------------------------------

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
        // Aligned with VoiceNoteControllerTest's documented contract: an
        // unsupported audio format is a validation failure on the upload
        // request (same family as a missing/empty file), not a
        // content-negotiation failure between client and server - so this
        // maps to 400, not 415.
        ProblemDetail problem = handler.handleUnsupportedAudioFormatException(
                new UnsupportedAudioFormatException("Only audio/webm and audio/mp4 are supported."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Only audio/webm and audio/mp4 are supported.");
    }

    @Test
    void handleStorageException_returns500WithFixedMessageNotTheRawExceptionMessage() {
        // The raw message can carry filesystem paths or bucket keys; the
        // handler deliberately does NOT echo ex.getMessage() to the client.
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
        ProblemDetail problem = handler.handleInvalidRefreshTokenException(
                new InvalidRefreshTokenException("This refresh token is invalid or has expired."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("This refresh token is invalid or has expired.");
    }
}
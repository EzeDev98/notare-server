package com.notare.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ask for a new email-verification code.
 *
 * <h2>Why the field changed from notarianId</h2>
 *
 * <p>The notarianId was previously the ONLY way to identify an account here,
 * and it is returned exactly once — in the body of the registration response.
 * The verification email contains the code and nothing else. So a user who
 * closed the tab between registering and verifying had no way back: they could
 * not verify (no id), could not resend (no id), and could not log in (the
 * account is unverified, which is a 403). The account was unreachable and the
 * email address was taken, so they could not even register again.
 *
 * <p>Accepting an email fixes that with the thing the user demonstrably still
 * has — they are reading the code in their inbox.
 *
 * <p>One field rather than two optional ones, following the precedent
 * {@code LoginRequest.usernameOrEmail} already set. Two nullable fields with a
 * "exactly one of these" rule is a cross-field constraint bean validation
 * cannot express, so it would live in the service and surface as a worse error
 * than a missing field does. One field cannot be half-filled.
 *
 * <p>The two forms are told apart by the presence of '@', which a notarianId
 * can never contain — the generator's alphabet is letters only.
 */
@Getter
@Setter
@NoArgsConstructor
public class ResendOtpRequest {

    @NotBlank(message = "Your Notarian ID or email address is required.")
    @Size(max = 100, message = "Identifier must not exceed 100 characters.")
    private String notarianIdOrEmail;
}
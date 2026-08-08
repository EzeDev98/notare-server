package com.notare.feature.auth.dto.request;

import com.notare.feature.auth.validation.ValidOtpCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Submit an email-verification code.
 *
 * <p>Accepts a notarianId or an email address for the same reason as
 * {@link ResendOtpRequest} — the notarianId is shown once, in the registration
 * response, and never appears again in anything the user keeps. See that class
 * for the full reasoning.
 *
 * <p>Note that the identifier is NOT a credential. Knowing someone's email does
 * not let you verify their account: you still need the six-digit code that was
 * sent only to that address, and it expires in ten minutes after five wrong
 * guesses. The identifier selects the account; the code proves you can read its
 * inbox.
 */
@Getter
@Setter
@NoArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "Your Notarian ID or email address is required.")
    @Size(max = 100, message = "Identifier must not exceed 100 characters.")
    private String notarianIdOrEmail;

    @NotBlank(message = "Code is required.")
    @ValidOtpCode
    private String code;
}
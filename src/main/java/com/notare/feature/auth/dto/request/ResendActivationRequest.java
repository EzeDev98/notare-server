package com.notare.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for sending a fresh account-activation email. */
@Getter
@Setter
@NoArgsConstructor
public class ResendActivationRequest {

    @NotBlank(message = "Your Notarian ID or email address is required.")
    @Size(max = 100, message = "Identifier must not exceed 100 characters.")
    private String notarianIdOrEmail;
}

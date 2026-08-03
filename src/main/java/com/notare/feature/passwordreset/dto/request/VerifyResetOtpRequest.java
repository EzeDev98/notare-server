package com.notare.feature.passwordreset.dto.request;

import com.notare.feature.auth.validation.ValidOtpCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VerifyResetOtpRequest {

    @NotBlank(message = "Email address is required.")
    @Email(message = "Please provide a valid email address.")
    private String email;

    @NotBlank(message = "Code is required.")
    @ValidOtpCode
    private String code;
}
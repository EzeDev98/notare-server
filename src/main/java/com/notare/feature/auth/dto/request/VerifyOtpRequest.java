package com.notare.feature.auth.dto.request;

import com.notare.feature.auth.validation.ValidOtpCode;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VerifyOtpRequest {
    @NotBlank(message = "Notarian ID is required.")
    private String notarianId;

    @NotBlank(message = "Code is required.")
    @ValidOtpCode
    private String code;
}
package com.notare.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VerifyOtpRequest {
    @NotNull(message = "User ID is required.")
    private Long userId;

    @NotBlank(message = "Code is required.")
    @Size(min = 6, max = 6, message = "Code must be 6 digits.")
    private String code;
}
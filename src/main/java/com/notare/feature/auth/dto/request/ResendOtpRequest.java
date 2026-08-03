package com.notare.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResendOtpRequest {
    @NotBlank(message = "Notarian ID is required.")
    private String notarianId;
}
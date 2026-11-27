package com.notare.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for refreshing a session. */
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required.")
    private String refreshToken;
}

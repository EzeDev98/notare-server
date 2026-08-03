package com.notare.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Username or email is required.")
    @Size(max = 100, message = "Username or email must not exceed 100 characters.")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required.")
    @Size(max = 64, message = "Password must not exceed 64 characters.")
    private String password;
}
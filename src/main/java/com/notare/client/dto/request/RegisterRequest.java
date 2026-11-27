package com.notare.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body sent to internal-auth-server to register. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String username;
    private String phoneNumber;
    private String email;
    private String password;
    private String confirmPassword;
}

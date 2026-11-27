package com.notare.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body sent to internal-auth-server to sign in. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String username;
    private String password;

    private String userAgent;

    public LoginRequest(String username, String password) {
        this(username, password, null);
    }
}

package com.notare.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for POST /api/v1/auth/login (Step 11A). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String username;
    private String password;

    /**
     * Step 17h: the person's own browser User-Agent. internal-auth-server labels the session with it
     * ("Mac, Chrome"); without it every device would be labelled with this server's HTTP client.
     */
    private String userAgent;

    public LoginRequest(String username, String password) {
        this(username, password, null);
    }
}

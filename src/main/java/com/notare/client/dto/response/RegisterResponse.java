package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** internal-auth-server's answer to a registration. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String internalAuthUserId;
    private String username;
    private String message;
}

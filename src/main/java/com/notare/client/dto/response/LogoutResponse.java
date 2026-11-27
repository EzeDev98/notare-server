package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** internal-auth-server's answer to a logout. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LogoutResponse {
    private String message;
    private int statusCode;
}

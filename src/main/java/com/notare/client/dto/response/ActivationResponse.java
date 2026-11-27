package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** internal-auth-server's answer to an activation request. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivationResponse {
    private String message;
}

package com.notare.client.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

/** internal-auth-server's answer to a session-status check. */
@Data
@NoArgsConstructor
public class SessionStatusResponse {
    private boolean active;
}

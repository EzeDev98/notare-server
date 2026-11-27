package com.notare.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body sent to internal-auth-server to wipe this app's users. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantWipeRequest {
    private String appId;
    private String confirmation;
}

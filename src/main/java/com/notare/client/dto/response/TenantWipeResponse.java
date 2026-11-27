package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** internal-auth-server's answer to a tenant wipe. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantWipeResponse {
    private String appId;
    private Map<String, Long> deletedCounts;
}

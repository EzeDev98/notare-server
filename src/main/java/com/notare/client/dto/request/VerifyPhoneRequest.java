package com.notare.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body sent to internal-auth-server to verify a phone number. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPhoneRequest {
    private String phoneNumber;
    private String otp;
}

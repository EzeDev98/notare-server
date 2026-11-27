package com.notare.feature.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Response to a registration request. */
@Getter
@Setter
@AllArgsConstructor
public class RegistrationResponse {

    private int statusCode;
    private String message;
    private String notarianId;
}

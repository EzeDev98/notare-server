package com.notare.feature.logout.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response to a logout request. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LogoutResponse {
    private int statusCode;
    private String message;
}

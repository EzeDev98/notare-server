package com.notare.feature.logout.web;

import com.notare.feature.logout.dto.response.LogoutResponse;
import com.notare.feature.logout.service.LogoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/logout")
public class LogoutController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LogoutService logoutService;

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = authorization.startsWith(BEARER_PREFIX) ? authorization.substring(BEARER_PREFIX.length()).trim() : null;
        LogoutResponse response = logoutService.logout(token);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<LogoutResponse> logoutAll(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = authorization.startsWith(BEARER_PREFIX) ? authorization.substring(BEARER_PREFIX.length()).trim() : null;
        LogoutResponse response = logoutService.logoutAll(token);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}

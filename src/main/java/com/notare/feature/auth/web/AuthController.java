package com.notare.feature.auth.web;

import com.notare.common.web.ClientIp;
import com.notare.common.web.UserAgent;

import com.notare.feature.auth.dto.request.RefreshTokenRequest;
import com.notare.feature.auth.dto.response.TokenResponse;
import com.notare.feature.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, @ClientIp String ipAddress, @UserAgent String userAgent) {
        TokenResponse response = authService.refreshToken(request, userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }
}
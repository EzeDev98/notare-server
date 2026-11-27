package com.notare.feature.auth.web;

import com.notare.common.web.ClientIp;
import com.notare.common.web.UserAgent;
import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.UserLoginResponse;
import com.notare.feature.auth.service.LoginService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint for signing in. */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/login")
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                   @ClientIp String ipAddress,
                                                   @UserAgent String userAgent) {
        UserLoginResponse response = loginService.login(request, userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }
}

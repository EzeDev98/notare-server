package com.notare.feature.auth.web;

import com.notare.common.web.ClientIp;
import com.notare.common.web.UserAgent;
import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.LoginResponse;
import com.notare.feature.auth.service.LoginService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/login")
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               @ClientIp String ipAddress,
                                               @UserAgent String userAgent) {
        LoginResponse response = loginService.login(request, userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }
}

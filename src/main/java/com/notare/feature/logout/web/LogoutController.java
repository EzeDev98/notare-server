package com.notare.feature.logout.web;

import com.notare.common.security.JwtAuthenticationFilter;
import com.notare.feature.logout.dto.response.LogoutResponse;
import com.notare.feature.logout.service.LogoutService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/logout")
public class LogoutController {

    private final LogoutService logoutService;

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestAttribute(value = JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, required = false) String sessionId, @AuthenticationPrincipal UserDetail userDetail) {
        LogoutResponse response = logoutService.logout(sessionId, userDetail.getUser().getId());
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<LogoutResponse> logoutAll(@AuthenticationPrincipal UserDetail userDetail) {
        LogoutResponse response = logoutService.logoutAll(userDetail.getUser().getId());
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}

package com.notare.feature.session.web;

import com.notare.common.security.JwtAuthenticationFilter;
import com.notare.feature.session.dto.response.SessionResponse;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/session")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(@AuthenticationPrincipal UserDetail userDetail, @RequestAttribute(value = JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, required = false) String currentSessionId) {
        List<SessionResponse> responses = sessionService.listSessions(userDetail.getUser().getId(), currentSessionId);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId, @AuthenticationPrincipal UserDetail userDetail) {
        sessionService.revokeSession(sessionId, userDetail.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}

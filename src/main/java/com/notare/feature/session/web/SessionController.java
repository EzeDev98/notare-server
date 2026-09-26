package com.notare.feature.session.web;

import com.notare.client.dto.response.SessionResponse;
import com.notare.feature.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/session")
public class SessionController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionService sessionService;

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(sessionService.listSessions(tokenFrom(authorization)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                              @PathVariable String sessionId) {
        sessionService.revokeSession(tokenFrom(authorization), sessionId);
        return ResponseEntity.noContent().build();
    }

    private String tokenFrom(String authorization) {
        return authorization.startsWith(BEARER_PREFIX) ? authorization.substring(BEARER_PREFIX.length()).trim() : "";
    }
}

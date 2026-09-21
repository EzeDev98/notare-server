package com.notare.feature.internal.web;

import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Receives user lifecycle pushes from internal-auth-server (IAM) so notare's
 * local user row never drifts from IAM, the source of truth.
 * <p>
 * Gated on a shared secret (internal-auth.user-events.secret), not a user
 * JWT. A blank secret disables the endpoint. Rows are never deleted -
 * notes, shares and collaborations reference them - only their status
 * changes: DELETED -> DEACTIVATED, DISABLED -> SUSPENDED, ENABLED -> ACTIVE
 * (only from SUSPENDED/DEACTIVATED).
 * <p>
 * Idempotent, and an unknown user is a 200 no-op, so IAM's best-effort
 * push can be safely repeated.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${app.title}/internal")
public class UserEventController {

    public record UserEventRequest(String internalAuthUserId, String event) { }

    private final UserRepository userRepository;

    @Value("${internal-auth.user-events.secret:}")
    private String secret;

    @PostMapping("/user-events")
    @Transactional
    public ResponseEntity<String> handle(@RequestHeader(value = "X-Internal-Secret", required = false) String provided,
                                         @RequestBody UserEventRequest request) {
        if (secret == null || secret.isBlank()
                || provided == null
                || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected user-event push - invalid, missing or unconfigured secret.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized.");
        }
        if (request.internalAuthUserId() == null || request.event() == null) {
            return ResponseEntity.badRequest().body("internalAuthUserId and event are required.");
        }

        User user = userRepository.findByInternalAuthId(request.internalAuthUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.ok("No local user - nothing to do.");
        }

        switch (request.event()) {
            case "DELETED" -> user.setUserStatus(UserStatus.DEACTIVATED);
            case "DISABLED" -> user.setUserStatus(UserStatus.SUSPENDED);
            case "ENABLED" -> {
                if (user.getUserStatus() == UserStatus.SUSPENDED || user.getUserStatus() == UserStatus.DEACTIVATED) {
                    user.setUserStatus(UserStatus.ACTIVE);
                }
            }
            default -> {
                return ResponseEntity.badRequest().body("Unknown event.");
            }
        }
        userRepository.save(user);
        log.info("Applied IAM user event {} to local user id={}", request.event(), user.getId());
        return ResponseEntity.ok("Applied.");
    }
}

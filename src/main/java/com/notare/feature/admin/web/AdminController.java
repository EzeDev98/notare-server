package com.notare.feature.admin.web;

import com.notare.client.service.InternalAuthClient;
import com.notare.feature.admin.service.DatabaseWipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Operator-only endpoints, called by hand from Swagger and never by the app. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${app.title}/admin")
public class AdminController {

    private final DatabaseWipeService databaseWipeService;
    private final InternalAuthClient authClient;

    @Value("${app.admin.wipe-secret}")
    private String wipeSecret;

    @PostMapping("/database/wipe")
    public ResponseEntity<String> wipeDatabase(@RequestHeader("X-Admin-Secret") String providedSecret) {
        if (wipeSecret.isBlank() || !secretMatches(providedSecret)) {
            log.warn("Rejected database wipe attempt — invalid or missing X-Admin-Secret.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin secret.");
        }

        log.warn("Database wipe triggered via POST /admin/database/wipe.");
        databaseWipeService.wipeAllData();

        try {
            authClient.wipeTenantData();
        } catch (RuntimeException e) {
            log.error("Local database wiped, but the internal-auth tenant wipe failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Local data wiped, but the internal-auth tenant wipe failed: " + e.getMessage()
                            + ". Call this endpoint again to retry.");
        }
        return ResponseEntity.ok("All data wiped (notare database and internal-auth tenant data).");
    }

    private boolean matches(String provided, String expected) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private boolean secretMatches(String providedSecret) {
        if (providedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                wipeSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8));
    }
}

package com.notare.feature.admin.web;

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

/**
 * Operator-only endpoints. Not wired into the client app anywhere, and
 * never should be — these are tools meant to be called by hand, from
 * Swagger, not by the product.
 *
 * <p>Sits OUTSIDE the normal JWT auth on purpose (see WebSecurityConfig —
 * this path is permitAll): a valid user session should be neither required
 * nor sufficient to wipe the database, since every ordinary user has one.
 * Gated instead on its own shared secret (app.admin.wipe-secret),
 * supplied as a header and checked here.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${app.title}/admin")
public class AdminController {

    private final DatabaseWipeService databaseWipeService;

    @Value("${app.admin.wipe-secret}")
    private String wipeSecret;

    /**
     * Deletes every row from every table in the connected database — dev's
     * local MySQL or prod's Neon Postgres, whichever this instance is
     * actually pointed at. There is no undo; nothing about this call is
     * scoped or partial.
     */
    @PostMapping("/database/wipe")
    public ResponseEntity<String> wipeDatabase(@RequestHeader("X-Admin-Secret") String providedSecret) {
        if (!secretMatches(providedSecret)) {
            log.warn("Rejected database wipe attempt — invalid or missing X-Admin-Secret.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin secret.");
        }

        log.warn("Database wipe triggered via POST /admin/database/wipe.");
        databaseWipeService.wipeAllData();
        return ResponseEntity.ok("All data wiped.");
    }

    /** Constant-time comparison — a plain .equals() leaks the secret's
     * correct-prefix length through response timing, which costs nothing
     * to avoid here. */
    private boolean secretMatches(String providedSecret) {
        if (providedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                wipeSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8));
    }
}

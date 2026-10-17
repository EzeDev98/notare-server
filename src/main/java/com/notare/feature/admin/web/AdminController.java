package com.notare.feature.admin.web;

import com.notare.client.service.InternalAuthClient;
import com.notare.feature.admin.service.DatabaseWipeService;
import com.notare.feature.admin.service.LegacyDataMigrationJob;
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
    private final InternalAuthClient authClient;
    private final LegacyDataMigrationJob legacyDataMigrationJob;

    /** Only one migration run at a time; the last result is kept for the status call. */
    private final AtomicBoolean migrationRunning = new AtomicBoolean(false);
    private volatile String lastMigrationResult = "never run";

    @Value("${app.admin.wipe-secret}")
    private String wipeSecret;

    /** Empty (the default) switches the migration endpoints off. */
    @Value("${app.admin.migration-secret:}")
    private String migrationSecret;

    /**
     * Deletes every row from every table in the connected database — dev's
     * local MySQL or prod's Neon Postgres, whichever this instance is
     * actually pointed at. There is no undo; nothing about this call is
     * scoped or partial.
     */
    @PostMapping("/database/wipe")
    public ResponseEntity<String> wipeDatabase(@RequestHeader("X-Admin-Secret") String providedSecret) {
        if (wipeSecret.isBlank() || !secretMatches(providedSecret)) {
            log.warn("Rejected database wipe attempt — invalid or missing X-Admin-Secret.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin secret.");
        }

        log.warn("Database wipe triggered via POST /admin/database/wipe.");
        databaseWipeService.wipeAllData();

        // Users also live in internal-auth-server; wipe this app's tenant there so the same
        // usernames/emails can register again. Safe to retry: both steps are idempotent.
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

    /**
     * Step 18e: starts the legacy data migration (LegacyDataMigrationJob) in the background. Own secret,
     * never the wipe secret. 202 when started, 409 when a run is already going. Safe to call again:
     * a second run changes nothing it has already migrated. Counts per table are logged and kept for the status call.
     */
    @PostMapping("/migration/legacy")
    public ResponseEntity<String> runLegacyMigration(@RequestHeader("X-Admin-Secret") String providedSecret) {
        if (migrationSecret.isBlank() || !matches(providedSecret, migrationSecret)) {
            log.warn("Rejected legacy migration attempt: invalid secret, or the migration secret is not set.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin secret.");
        }
        if (!migrationRunning.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A migration run is already in progress.");
        }
        lastMigrationResult = "running";
        CompletableFuture.runAsync(() -> {
            try {
                lastMigrationResult = legacyDataMigrationJob.run().toString();
            } catch (RuntimeException e) {
                log.error("Legacy data migration failed", e);
                lastMigrationResult = "failed: " + e.getMessage();
            } finally {
                migrationRunning.set(false);
            }
        });
        return ResponseEntity.accepted().body("Migration started. Call /admin/migration/legacy/status for the result.");
    }

    /** The result of the last run (counts per table), or "running" / "never run". POST so it matches the permit rule. */
    @PostMapping("/migration/legacy/status")
    public ResponseEntity<Map<String, String>> legacyMigrationStatus(@RequestHeader("X-Admin-Secret") String providedSecret) {
        if (migrationSecret.isBlank() || !matches(providedSecret, migrationSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("running", String.valueOf(migrationRunning.get()), "result", lastMigrationResult));
    }

    private boolean matches(String provided, String expected) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
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

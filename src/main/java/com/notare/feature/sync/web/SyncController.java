package com.notare.feature.sync.web;

import com.notare.feature.sync.dto.request.SyncBatchRequest;
import com.notare.feature.sync.dto.response.SyncBatchResponse;
import com.notare.feature.sync.dto.response.SyncPullResponse;
import com.notare.feature.sync.service.SyncPullService;
import com.notare.feature.sync.service.SyncPushService;
import com.notare.feature.user.model.UserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * The offline client's two endpoints: pull what changed, push what I queued.
 *
 * <p>A collection of its own rather than sync verbs bolted onto /notes. Sync
 * isn't an operation on a note — it's a protocol about a whole dataset, with
 * its own cursor, its own batching, and its own error model. Hanging it off
 * /notes would also make it awkward to extend later, since a real sync feed
 * eventually covers more than one entity.
 *
 * <p>Both endpoints are scoped to the caller and carry no user id. Protected by
 * default through the security allowlist's closing anyRequest().authenticated().
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/sync")
public class SyncController {

    private final SyncPullService syncPullService;
    private final SyncPushService syncPushService;

    /**
     * What changed since my cursor.
     *
     * <p>{@code since} is optional: omitting it means "first sync", which
     * returns active notes only and no tombstones — a client with nothing
     * cached has nothing to evict.
     *
     * <p><b>There is deliberately no sort parameter.</b> Delta paging is only
     * correct under one specific ordering (updatedAt then id), which the note
     * module imposes and any caller-supplied sort would be discarded by.
     * Accepting a Pageable here would advertise a {@code sort} parameter that
     * the server silently ignores — an API surface that lies about what it
     * honours is worse than one that offers less. Explicit page and size say
     * exactly what's on offer.
     *
     * <p>Always send back the {@code nextCursor} from the response rather than
     * a locally computed time. While {@code hasMore} is true it comes back
     * unchanged, meaning "same window, next page" — advancing the cursor early
     * would skip the rest of the window permanently.
     */
    @GetMapping
    public ResponseEntity<SyncPullResponse> pull(
            @AuthenticationPrincipal UserDetail userDetail,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        // Size is clamped in the service against SyncProperties. Doing it there
        // rather than here keeps the ceiling in one place, where the reason for
        // it lives.
        SyncPullResponse response = syncPullService.pull(
                userDetail.getUser().getId(), since, PageRequest.of(page, Math.max(size, 1)));
        log.info("sync pull user={} page={} changed={} deleted={} clips={} hasMore={}",
                userDetail.getUser().getId(), page, count(response.changed()), count(response.deletedIds()),
                count(response.clips()), response.hasMore());
        return ResponseEntity.ok(response);
    }

    /**
     * Replay a queue of offline operations.
     *
     * <p><b>200, even when every operation conflicted.</b> The batch itself was
     * processed successfully; what happened to each operation is application
     * state, not a transport failure. A 4xx would force the client to parse an
     * error body to find the operations that actually worked.
     *
     * <p>Not 207 Multi-Status either, though it's tempting. 207 is defined by
     * WebDAV with an XML multistatus body, so using it for a JSON result array
     * is a stretch, and intermediaries handle it unevenly. A plain 200 with a
     * per-operation status field is the ordinary REST answer and needs no
     * explanation to a client author.
     *
     * <p>A malformed BATCH — empty list, over the size cap, an operation
     * missing operationId or type — is a 400 from bean validation before any of
     * this runs. That's the right line: those are structural failures of the
     * request, whereas a missing version on one operation is a per-operation
     * outcome that must not take its nineteen valid neighbours down with it.
     *
     * <p>The endpoint is safe to retry in full. Anything already applied comes
     * back as APPLIED via the ledger, so a client that never saw the response
     * simply sends the same batch again.
     */
    @PostMapping("/batch")
    public ResponseEntity<SyncBatchResponse> push(
            @AuthenticationPrincipal UserDetail userDetail,
            @Valid @RequestBody SyncBatchRequest request) {

        SyncBatchResponse response = syncPushService.push(userDetail.getUser().getId(), request);
        log.info("sync push user={} operations={} results={}",
                userDetail.getUser().getId(), count(request.getOperations()), count(response.results()));
        return ResponseEntity.ok(response);
    }

    private static int count(java.util.List<?> list) {
        return list == null ? 0 : list.size();
    }
}
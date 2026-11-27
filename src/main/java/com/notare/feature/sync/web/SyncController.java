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

/** Endpoints for pulling changes and pushing offline operations. */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/sync")
public class SyncController {

    private final SyncPullService syncPullService;
    private final SyncPushService syncPushService;

    @GetMapping
    public ResponseEntity<SyncPullResponse> pull(
            @AuthenticationPrincipal UserDetail userDetail,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        SyncPullResponse response = syncPullService.pull(
                userDetail.getUser().getId(), since, PageRequest.of(page, Math.max(size, 1)));
        log.info("sync pull user={} page={} changed={} deleted={} clips={} hasMore={}",
                userDetail.getUser().getId(), page, count(response.changed()), count(response.deletedIds()),
                count(response.clips()), response.hasMore());
        return ResponseEntity.ok(response);
    }

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

package com.notare.feature.collab.web;

import com.notare.feature.collab.dto.response.CollabTicketResponse;
import com.notare.feature.collab.service.CollabTicketService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/notes")
public class CollabTicketController {

    private final CollabTicketService collabTicketService;

    /**
     * Browsers cannot set an Authorization header on a WebSocket, so the client
     * asks here (with its normal JWT) and opens the socket with the ticket.
     */
    @PostMapping("/{noteId}/collab-ticket")
    public ResponseEntity<CollabTicketResponse> collabTicket(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        return ResponseEntity.ok(
                collabTicketService.issueTicket(userDetail.getUser().getId(), noteId));
    }
}

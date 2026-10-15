package com.notare.feature.collab.service;

import com.notare.feature.collab.dto.response.CollabTicketResponse;
import com.notare.feature.collab.enums.CollabRole;

import java.util.Optional;

public interface CollabTicketService {

    /**
     * Issues a one-time ticket for opening a live editing socket on a note.
     * The caller must be the note's owner or hold a share on it, and the note
     * must not be in the trash. Anyone else gets a 404, so the response never
     * confirms that someone else's note exists.
     */
    CollabTicketResponse issueTicket(Long userId, Long noteId);

    /**
     * The caller's role on the note right now, or empty when they have no access.
     * The handshake asks this again, because a ticket can outlive the access it
     * was issued for by up to 60 seconds.
     */
    Optional<CollabRole> currentRole(Long userId, Long noteId);
}

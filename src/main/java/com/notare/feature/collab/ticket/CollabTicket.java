package com.notare.feature.collab.ticket;

import com.notare.feature.collab.enums.CollabRole;

/** What a ticket stands for: this user, on this note, with this role. */
public record CollabTicket(Long userId, Long noteId, CollabRole role) {
}

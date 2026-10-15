package com.notare.feature.collab.dto.response;

/**
 * What the client gets back to open a socket (step 15c): the one-time ticket and
 * the role it was issued for, so the editor can switch to view-only before the
 * connection is even open.
 */
public record CollabTicketResponse(String ticket, String role) {
}

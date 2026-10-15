package com.notare.feature.collab.ticket;

import java.time.Duration;
import java.util.Optional;

/**
 * Where one-time tickets live between the HTTP call that issues them and the
 * WebSocket handshake that redeems them (step 15c).
 *
 * <p>An interface so a test can swap in an in-memory store and the production
 * one can be Redis, which every instance of the server can see.
 */
public interface CollabTicketStore {

    /** Keeps the ticket for {@code ttl}, then forgets it. */
    void put(String ticket, CollabTicket value, Duration ttl);

    /**
     * Returns the ticket's value and deletes it in the same step, so a ticket
     * works once. Empty for an unknown, expired or already used ticket.
     */
    Optional<CollabTicket> redeem(String ticket);
}

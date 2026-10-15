package com.notare.feature.collab.socket;

import com.notare.feature.collab.enums.CollabRole;
import com.notare.feature.collab.service.CollabTicketService;
import com.notare.feature.collab.ticket.CollabTicket;
import com.notare.feature.collab.ticket.CollabTicketStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Redeems the one-time ticket during the handshake (step 15c).
 *
 * <p>No ticket, an unknown or expired one, or one already used: the handshake
 * is refused with 401 and no socket ever exists. A valid ticket is deleted as it
 * is read, and the user, note and role it stood for are attached to the session.
 * The role is never taken from the client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabHandshakeInterceptor implements HandshakeInterceptor {

    static final String TICKET_PARAM = "ticket";

    private final CollabTicketStore ticketStore;
    private final CollabTicketService ticketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(TICKET_PARAM);

        if (!StringUtils.hasText(ticket)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<CollabTicket> redeemed;
        try {
            redeemed = ticketStore.redeem(ticket);
        } catch (RuntimeException storeDown) {
            // Redis is unreachable. Not the caller's fault, and not a reason to let anyone in.
            log.warn("Collaboration ticket store unavailable", storeDown);
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }

        if (redeemed.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        CollabTicket value = redeemed.get();

        // A ticket lives for 60 seconds, so access may have been revoked or changed since
        // it was issued. Check again and use the role as it is now.
        CollabRole role;
        try {
            role = ticketService.currentRole(value.userId(), value.noteId()).orElse(null);
        } catch (RuntimeException databaseDown) {
            log.warn("Could not re-check collaboration access", databaseDown);
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        if (role == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(CollabAttributes.USER_ID, value.userId());
        attributes.put(CollabAttributes.NOTE_ID, value.noteId());
        attributes.put(CollabAttributes.ROLE, role);
        attributes.put(CollabAttributes.LAST_SEEN, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after the upgrade.
    }
}

package com.notare.feature.collab.service.impl;

import com.notare.common.exception.NoteNotFoundException;
import com.notare.feature.collab.dto.response.CollabTicketResponse;
import com.notare.feature.collab.enums.CollabRole;
import com.notare.feature.collab.service.CollabTicketService;
import com.notare.feature.collab.ticket.CollabTicket;
import com.notare.feature.collab.ticket.CollabTicketStore;
import com.notare.feature.note.repository.NoteRepository;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CollabTicketServiceImpl implements CollabTicketService {

    /** Long enough to open a socket, short enough that a leaked ticket is useless. */
    static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final NoteRepository noteRepository;
    private final NoteShareRepository noteShareRepository;
    private final CollabTicketStore ticketStore;

    @Override
    @Transactional(readOnly = true)
    public CollabTicketResponse issueTicket(Long userId, Long noteId) {
        CollabRole role = roleFor(userId, noteId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));

        String ticket = newTicket();
        ticketStore.put(ticket, new CollabTicket(userId, noteId, role), TICKET_TTL);

        return new CollabTicketResponse(ticket, role.wire());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CollabRole> currentRole(Long userId, Long noteId) {
        return roleFor(userId, noteId);
    }

    /**
     * Owner, editor, viewer, or nothing. A note in the trash is invisible to
     * everyone, the owner included, exactly as it is for every other endpoint.
     */
    private Optional<CollabRole> roleFor(Long userId, Long noteId) {
        if (noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, userId).isPresent()) {
            return Optional.of(CollabRole.OWNER);
        }

        Optional<NoteShare> share = noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, userId);
        if (share.isEmpty() || noteRepository.findByIdAndDeletedFalse(noteId).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(share.get().getPermission().satisfies(SharePermission.WRITE)
                ? CollabRole.EDITOR
                : CollabRole.VIEWER);
    }

    /** 32 random bytes, URL-safe: 256 bits, so it cannot be guessed in 60 seconds. */
    private static String newTicket() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

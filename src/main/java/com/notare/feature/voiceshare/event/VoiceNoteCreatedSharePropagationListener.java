package com.notare.feature.voiceshare.event;

import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import com.notare.feature.voicenote.event.VoiceNoteCreatedEvent;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.service.VoiceShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Propagates a newly-created voice note to everyone who already has access
 * to its parent note.
 *
 * <p>If a note is already shared and the owner records another voice note
 * against that note later, the new recording inherits the existing note
 * shares.
 *
 * <p>Standalone voice notes (noteId == null) remain independently shareable.
 */
@Component
@RequiredArgsConstructor
public class VoiceNoteCreatedSharePropagationListener {

    private static final int MAX_NOTE_SHARES_PER_EVENT = 500;

    private final NoteShareRepository noteShareRepository;
    private final VoiceShareService voiceShareService;

    @EventListener
    public void onVoiceNoteCreated(VoiceNoteCreatedEvent event) {
        if (event.noteId() == null) {
            return;
        }

        var noteShares = noteShareRepository.findByNoteIdAndOwnerId(
                event.noteId(),
                event.ownerId(),
                PageRequest.of(0, MAX_NOTE_SHARES_PER_EVENT)
        );

        for (NoteShare noteShare : noteShares.getContent()) {
            ShareVoiceNoteRequest request = new ShareVoiceNoteRequest();
            request.setSharedWithUserId(noteShare.getSharedWithUserId());
            request.setPermission(noteShare.getPermission());

            try {
                voiceShareService.shareVoiceNote(
                        event.ownerId(),
                        event.voiceNoteId(),
                        request
                );
            } catch (RuntimeException ignored) {
                /*
                 * The voice note has already been successfully created.
                 * VoiceShareService owns validation and duplicate protection.
                 * A propagation failure must not make a successful recording
                 * disappear from the owner's note.
                 */
            }
        }
    }
}

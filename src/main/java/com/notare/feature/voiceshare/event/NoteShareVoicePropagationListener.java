package com.notare.feature.voiceshare.event;

import com.notare.feature.share.dto.request.ShareNoteRequest;
import com.notare.feature.share.event.NoteSharedEvent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.service.VoiceShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Propagates a note share to all voice notes currently attached to that note.
 *
 * <p>A voice note attached to a note is part of that note's shared content.
 * The owner should therefore not have to separately grant access to every
 * recording whenever the parent note is shared.
 *
 * <p>This listener runs BEFORE_COMMIT through the surrounding transaction.
 * If propagation fails, the parent NoteShare transaction fails as well,
 * preventing a note from being shared without its existing attached
 * recordings.
 */
@Component
@RequiredArgsConstructor
public class NoteShareVoicePropagationListener {

    private final VoiceNoteService voiceNoteService;
    private final VoiceShareService voiceShareService;

    @EventListener
    @Transactional
    public void onNoteShared(NoteSharedEvent event) {
        List<VoiceNoteResponse> voiceNotes =
                voiceNoteService.listVoiceNotesForNote(event.ownerId(), event.noteId());

        if (voiceNotes.isEmpty()) {
            return;
        }

        ShareNoteRequest request = new ShareNoteRequest();
        request.setSharedWithUserId(event.sharedWithUserId());
        request.setPermission(event.permission());

        for (VoiceNoteResponse voiceNote : voiceNotes) {
            voiceShareService.shareVoiceNote(
                    event.ownerId(),
                    voiceNote.id(),
                    toVoiceShareRequest(request)
            );
        }
    }

    private com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest toVoiceShareRequest(
            ShareNoteRequest request) {

        com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest voiceRequest =
                new com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest();

        voiceRequest.setSharedWithUserId(request.getSharedWithUserId());
        voiceRequest.setPermission(request.getPermission());

        return voiceRequest;
    }
}

package com.notare.feature.voiceshare.event;

import com.notare.feature.share.dto.request.ShareNoteRequest;
import com.notare.feature.share.event.NoteSharedEvent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.service.VoiceShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Propagates a note share to all voice notes currently attached to that note.
 *
 * <p>A voice note attached to a note is part of that note's shared content.
 * Whoever recorded it should therefore not have to separately grant access to
 * it whenever the parent note is shared with someone new.
 *
 * <p>Each grant is created FROM THE RECORDING'S OWN OWNER
 * ({@code voiceNote.ownerId()}), not from {@code event.ownerId()} (the NOTE's
 * owner) — those are the same person for a recording the note owner made
 * themselves, but a WRITE collaborator can now attach their own recordings
 * too (see VoiceNoteServiceImpl#listVoiceNotesForNote's own doc comment on
 * that change). {@link VoiceShareService#shareVoiceNote} is an OWNER
 * operation — "grant access to one of MY voice notes" — so it must be called
 * as whoever actually owns the recording, or a collaborator's own reply
 * would 404 here (the note owner does not own it) the moment a note carries
 * more than one person's recordings.
 *
 * <p>BEST-EFFORT PER VOICE NOTE, unlike a straightforward propagation. This
 * listener still runs BEFORE_COMMIT through the surrounding transaction, but
 * a single recording's grant failing no longer fails the whole NoteShare
 * transaction. That was safe to guarantee back when every recording's real
 * owner was necessarily the note's owner — the same collaboration check that
 * gated the note share itself had already passed for that pair, so this
 * could not legitimately fail. It is no longer safe to guarantee once a
 * recording can belong to a different collaborator: {@code shareVoiceNote}
 * also requires the RECORDING'S owner and the new recipient to be accepted
 * collaborators of EACH OTHER, a relationship the note owner's own sharing
 * action says nothing about. Failing the entire note share because two other
 * people haven't connected as collaborators would be surprising and
 * disproportionate — the new recipient still reaches every attachment
 * through the note itself (SharedNoteService's grant-gated list/download),
 * this direct per-recording grant is only what additionally unlocks the
 * standalone `/shared-voice/:id` link for that one recording.
 */
@Slf4j
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
            try {
                voiceShareService.shareVoiceNote(
                        voiceNote.ownerId(),
                        voiceNote.id(),
                        toVoiceShareRequest(request)
                );
            } catch (RuntimeException ex) {
                // Best-effort — see this class's own doc comment on why one
                // recording's grant failing must not fail the note share
                // itself. The recipient still reaches this recording through
                // the note's own attached-voice-notes listing regardless.
                log.warn("Could not propagate note share to voice note {} (owned by {}): {}",
                        voiceNote.id(), voiceNote.ownerId(), ex.getMessage());
            }
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

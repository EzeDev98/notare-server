package com.notare.feature.voiceshare.event;

import com.notare.feature.share.event.NoteSharePermissionChangedEvent;
import com.notare.feature.share.event.NoteShareRevokedEvent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.model.VoiceShare;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps voice-note access aligned with the access granted to the parent note.
 *
 * <p>A voice note attached to a shared note inherits the same recipient and
 * permission. The note-share module publishes the fact that access changed;
 * this listener owns the corresponding VoiceShare rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteShareVoiceAccessSyncListener {

    private final VoiceNoteService voiceNoteService;
    private final VoiceShareRepository voiceShareRepository;

    /**
     * A note permission changed, so every attached voice note must receive
     * the same permission for that recipient.
     */
    @EventListener
    public void onNoteSharePermissionChanged(NoteSharePermissionChangedEvent event) {
        List<VoiceNoteResponse> voiceNotes =
                voiceNoteService.listVoiceNotesForNote(
                        event.ownerId(),
                        event.noteId());

        for (VoiceNoteResponse voiceNote : voiceNotes) {
            VoiceShare share = voiceShareRepository
                    .findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                            voiceNote.id(),
                            event.ownerId(),
                            event.sharedWithUserId())
                    .orElse(null);

            if (share == null) {
                log.debug(
                        "No voice share found while synchronizing note permission: " +
                        "noteId={}, voiceNoteId={}, ownerId={}, recipientId={}",
                        event.noteId(),
                        voiceNote.id(),
                        event.ownerId(),
                        event.sharedWithUserId()
                );
                continue;
            }

            if (share.getPermission() == event.newPermission()) {
                continue;
            }

            share.setPermission(event.newPermission());
            voiceShareRepository.save(share);
        }
    }

    /**
     * A note share was revoked, so every attached voice-note grant for the
     * same recipient must also disappear.
     */
    @EventListener
    public void onNoteShareRevoked(NoteShareRevokedEvent event) {
        List<VoiceNoteResponse> voiceNotes =
                voiceNoteService.listVoiceNotesForNote(
                        event.ownerId(),
                        event.noteId());

        for (VoiceNoteResponse voiceNote : voiceNotes) {
            voiceShareRepository
                    .findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                            voiceNote.id(),
                            event.ownerId(),
                            event.sharedWithUserId())
                    .ifPresent(voiceShareRepository::delete);
        }
    }
}

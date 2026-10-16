package com.notare.feature.voicenote.event;

import com.notare.feature.user.event.UserDeletionRequested;
import org.springframework.context.event.EventListener;

import com.notare.common.storage.FileStorageService;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.note.event.NoteRestoredEvent;
import com.notare.feature.note.event.NoteSoftDeletedEvent;
import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.repository.VoiceNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Voice clips live and die with their note (step 17b, B-09, P-02): hidden when it goes to the
 * trash, back when it is restored, gone for good when it is deleted for good. Never orphaned.
 *
 * <p>BEFORE_COMMIT, for the reason NoteShareCleanupListener gives: these change rows, so they must
 * commit or roll back together with the note change that caused them. The stored FILES are the
 * exception: they are removed only after the commit, so a rollback never leaves a row whose bytes
 * are gone. A failed file removal is logged and tolerated, as in VoiceNoteService#deleteVoiceNote.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceNoteLifecycleListener {

    private final VoiceNoteRepository voiceNotes;
    private final FileStorageService fileStorage;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNoteSoftDeleted(NoteSoftDeletedEvent event) {
        int hidden = voiceNotes.softDeleteByNoteId(event.noteId(), LocalDateTime.now());
        log.debug("Hid {} voice clip(s) of trashed note {}", hidden, event.noteId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNoteRestored(NoteRestoredEvent event) {
        int shown = voiceNotes.restoreByNoteId(event.noteId(), LocalDateTime.now());
        log.debug("Restored {} voice clip(s) of note {}", shown, event.noteId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNotePermanentlyDeleted(NotePermanentlyDeletedEvent event) {
        int removed = removeClips(voiceNotes.findAllByNoteIdIncludingDeleted(event.noteId()));
        log.debug("Removed {} voice clip(s) of permanently deleted note {}", removed, event.noteId());
    }

    /**
     * Account deletion (step 17g, B-03): every clip this user uploaded, in their own notes and in
     * other people's. A plain listener, so it runs inside the deletion's transaction.
     */
    @EventListener
    public void onUserDeletionRequested(UserDeletionRequested event) {
        int removed = removeClips(voiceNotes.findAllByOwnerIdIncludingDeleted(event.userId()));
        log.debug("Removed {} voice clip(s) of account {}", removed, event.userId());
    }

    /** Deletes the rows now and the stored files after the commit. Returns how many clips went. */
    private int removeClips(List<VoiceNote> clips) {
        if (clips.isEmpty()) {
            return 0;
        }
        List<String> storageKeys = clips.stream().map(VoiceNote::getStorageKey).toList();
        for (VoiceNote clip : clips) {
            voiceNotes.deleteRowById(clip.getId());
            // Voice shares on the clip go with it, through the same event a single delete uses.
            eventPublisher.publishEvent(
                    new VoiceNoteDeletedEvent(clip.getId(), clip.getOriginalFilename(), clip.getOwnerId()));
        }
        removeFilesAfterCommit(storageKeys);
        return clips.size();
    }

    private void removeFilesAfterCommit(List<String> storageKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String key : storageKeys) {
                    try {
                        fileStorage.delete(key);
                    } catch (RuntimeException ex) {
                        log.warn("Could not remove stored file {} of a deleted voice clip: {}", key, ex.getMessage());
                    }
                }
            }
        });
    }
}

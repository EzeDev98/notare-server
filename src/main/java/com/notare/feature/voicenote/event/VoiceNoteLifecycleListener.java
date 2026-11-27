package com.notare.feature.voicenote.event;

import com.notare.feature.user.event.UserDeletionRequested;
import org.springframework.context.event.EventListener;

import com.notare.common.storage.FileStorageService;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
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

import java.util.List;

/** Deletes a note's voice clips when the note is deleted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceNoteLifecycleListener {

    private final VoiceNoteRepository voiceNotes;
    private final FileStorageService fileStorage;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNotePermanentlyDeleted(NotePermanentlyDeletedEvent event) {
        int removed = removeClips(voiceNotes.findAllByNoteIdIncludingDeleted(event.noteId()));
        log.debug("Removed {} voice clip(s) of permanently deleted note {}", removed, event.noteId());
    }

    @EventListener
    public void onUserDeletionRequested(UserDeletionRequested event) {
        int removed = removeClips(voiceNotes.findAllByOwnerIdIncludingDeleted(event.userId()));
        log.debug("Removed {} voice clip(s) of account {}", removed, event.userId());
    }

    private int removeClips(List<VoiceNote> clips) {
        if (clips.isEmpty()) {
            return 0;
        }
        List<String> storageKeys = clips.stream().map(VoiceNote::getStorageKey).toList();
        for (VoiceNote clip : clips) {
            voiceNotes.deleteRowById(clip.getId());
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

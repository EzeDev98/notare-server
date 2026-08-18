package com.notare.feature.voiceshare.event;

import com.notare.feature.collaboration.event.CollaborationRemovedEvent;
import com.notare.feature.voicenote.event.VoiceNoteDeletedEvent;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps voice-note grants from outliving whatever justified them. Mirrors
 * {@link com.notare.feature.share.event.NoteShareCleanupListener}'s two
 * handlers, but the two use DIFFERENT transactional semantics — that
 * divergence is deliberate, not an inconsistency, and is explained below.
 *
 * <h2>onCollaborationRemoved — BEFORE_COMMIT, same as the note-share listener</h2>
 *
 * <p>Collaboration removal (CollaborationService#cancel) IS transactional, so
 * the same reasoning as NoteShareCleanupListener applies unchanged: this
 * listener revokes access, so it must run BEFORE_COMMIT, inside the original
 * transaction, so the grant deletes commit atomically with the cancel and an
 * exception rolls both back. An AFTER_COMMIT here would let the cancel
 * succeed while a stale voice-share grant quietly survived — a silent
 * security failure.
 *
 * <h2>onVoiceNoteDeleted — fallbackExecution = true, NOT BEFORE_COMMIT</h2>
 *
 * <p>{@code VoiceNoteServiceImpl#deleteVoiceNote} is deliberately NOT
 * {@code @Transactional} (it separates the DB delete from best-effort
 * filesystem cleanup, and a DB transaction can't roll back a filesystem
 * write anyway). {@code @TransactionalEventListener(phase = BEFORE_COMMIT)}
 * only fires when there IS a transaction to bind to — with no enclosing
 * transaction, a BEFORE_COMMIT listener is simply never invoked, and this
 * cleanup would silently never run. That is exactly the failure mode called
 * out in NoteShareCleanupListener's own doc comment (an obsolete event type
 * that "compiled, started, and logged nothing") — copying the BEFORE_COMMIT
 * pattern here would reproduce it on purpose.
 *
 * <p>{@code fallbackExecution = true} is the documented escape hatch: run
 * immediately, synchronously, in the publishing thread, if no transaction is
 * active — which is always true here, so this listener behaves like an
 * ordinary (non-transactional) event listener for this event specifically.
 * This is accepted as a best-effort cleanup, consistent with
 * {@code deleteVoiceNote}'s own best-effort storage deletion a few lines
 * above where this event is published: the voice note is already gone
 * either way, and a cleanup failure here leaves at most an orphaned grant
 * row rather than a security hole (the grant now points at a voice note that
 * no longer resolves, so SharedVoiceNoteServiceImpl's null-filtering in
 * listSharedWithMe already hides it — see that class's KNOWN INCONSISTENCY
 * note).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceShareCleanupListener {

    private final VoiceShareRepository voiceShareRepository;

    /**
     * The relationship ended, so every voice note this owner shared with that
     * person is revoked. Scoped to the pair, same as NoteShareCleanupListener.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCollaborationRemoved(CollaborationRemovedEvent event) {
        log.debug("Revoking voice shares from owner {} to user {}", event.ownerId(), event.collaboratorId());
        voiceShareRepository.deleteByOwnerIdAndSharedWithUserId(event.ownerId(), event.collaboratorId());
    }

    /**
     * The voice note is gone for good, so its grants go with it.
     *
     * <p>Same "why scope by voiceNoteId alone" reasoning as
     * NoteShareCleanupListener#onNotePermanentlyDeleted: every grant on a
     * deleted voice note goes, regardless of who granted it. The event's
     * ownerId isn't needed for the delete itself.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    public void onVoiceNoteDeleted(VoiceNoteDeletedEvent event) {
        log.debug("Revoking all voice shares on deleted voice note {}", event.voiceNoteId());
        voiceShareRepository.deleteByVoiceNoteId(event.voiceNoteId());
    }
}

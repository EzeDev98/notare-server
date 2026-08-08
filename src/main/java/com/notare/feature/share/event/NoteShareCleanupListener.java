package com.notare.feature.share.event;

import com.notare.feature.collaboration.event.CollaborationRemovedEvent;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.share.repository.NoteShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps grants from outliving whatever justified them.
 *
 * <p>Two ways a grant can become stale, and both are handled here rather than
 * in the module that caused them — the share module owns its own rows, and the
 * collaboration and note modules stay unaware that sharing exists.
 *
 * <h2>Why BEFORE_COMMIT, not AFTER_COMMIT</h2>
 *
 * Step 5's transcription listener and the registration OTP listener both use
 * AFTER_COMMIT with @Async, and that's right for them: they're slow
 * side-effects, and if one fails the worst case is a missing transcript or an
 * unsent email. Nothing unsafe survives.
 *
 * <p>These listeners are the opposite. They REVOKE ACCESS. If cleanup were
 * fired after commit and then failed — a dropped connection, a constraint, a
 * restart mid-flight — the cancel would already be durable while the grants
 * quietly survived, leaving a removed collaborator with live access to notes
 * and no error anywhere to say so. That is a security failure, and a silent one.
 *
 * <p>BEFORE_COMMIT runs the listener inside the original transaction, so the
 * deletes commit atomically with the cause and an exception rolls BOTH back.
 * The owner sees the cancel fail and retries; they never see it succeed while
 * access lingers. Failing loudly and together beats succeeding halfway.
 *
 * <p>No @Async here for the same reason — an async listener runs on another
 * thread with its own transaction, which throws away the atomicity this whole
 * design is buying.
 *
 * <h2>Why both event types are imported explicitly</h2>
 *
 * <p>Both handler parameters name a type from ANOTHER module, and both imports
 * above are load-bearing. This class previously carried neither, because
 * same-package copies of both events existed in {@code feature.share.event} —
 * so the unqualified names resolved to those local copies instead. One of them
 * ({@code NotePermanentlyDeletedEvent}) was an obsolete two-field version that
 * nothing published, which meant this listener silently never fired and
 * permanently-deleted notes left their grants behind. It compiled, started, and
 * logged nothing.
 *
 * <p>Java's same-package resolution wins over intent, with no compiler
 * complaint. The defence is that an event now lives in the module that
 * publishes it, so there is no local copy for a name to bind to by accident.
 * If a duplicate ever reappears, {@code ShareCleanupIntegrationTest} fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteShareCleanupListener {

    private final NoteShareRepository noteShareRepository;

    /**
     * The relationship ended, so every note this owner shared with that person
     * is revoked. Scoped to the pair: other people's grants on the same notes,
     * and this person's grants from other owners, are untouched.
     *
     * <p>Deleting nothing is a perfectly normal outcome — most cancelled
     * invitations never had a note shared under them.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCollaborationRemoved(CollaborationRemovedEvent event) {
        log.debug("Revoking note shares from owner {} to user {}", event.ownerId(), event.collaboratorId());
        noteShareRepository.deleteByOwnerIdAndSharedWithUserId(event.ownerId(), event.collaboratorId());
    }

    /**
     * The note is gone for good, so its grants go with it.
     *
     * <p>Without this the rows are orphans pointing at a dead id — and since
     * ids are reused by nothing in MySQL AUTO_INCREMENT under normal operation
     * but can be after a table reset or restore, an orphan grant is a latent
     * access bug rather than merely untidy data. Cheap to prevent, awkward to
     * diagnose later.
     *
     * <p>The event carries {@code ownerId} as well, which this handler doesn't
     * need: every grant on a permanently deleted note goes, regardless of who
     * granted it. Scoping the delete by owner as well would be a no-op today
     * (a note has exactly one owner and it is immutable) and would silently
     * leak grants the day that stops being true.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNotePermanentlyDeleted(NotePermanentlyDeletedEvent event) {
        log.debug("Revoking all shares on permanently deleted note {}", event.noteId());
        noteShareRepository.deleteByNoteId(event.noteId());
    }
}
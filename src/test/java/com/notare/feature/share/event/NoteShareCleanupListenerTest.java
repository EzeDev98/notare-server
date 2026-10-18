package com.notare.feature.share.event;

import com.notare.feature.collaboration.event.CollaborationRemovedEvent;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.share.repository.NoteShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 8d of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito is unavailable here (see
 * {@link UserServiceImplementationTest}
 * for the full explanation). Reasoned from source; run via {@code mvn test}
 * before trusting it.
 *
 * <p>Covers {@link NoteShareCleanupListener}'s two handlers. There isn't much
 * branching in the handler bodies themselves — each is a one-line delegation
 * to a repository delete — so what this suite locks in is the thing that
 * actually broke once, per the class's own extensive doc comment: EACH
 * handler is wired to the event type from the module that PUBLISHES it
 * ({@link CollaborationRemovedEvent} from the collaboration module, {@link
 * NotePermanentlyDeletedEvent} from the note module), not to a same-package
 * duplicate. The class's doc comment explains that an obsolete same-package
 * copy of {@code NotePermanentlyDeletedEvent} once shadowed the real one via
 * Java's same-package name resolution, and the listener silently never fired
 * — it compiled, started, and logged nothing. These tests call each handler
 * with the REAL imported event type directly, so a regression back to a
 * shadowing local copy would show up as a compile error here (a locally
 * redeclared type reachable unqualified would no longer match the imported
 * one this file references) rather than a silent runtime no-op.
 *
 * <p>Both handlers scope their delete narrowly rather than broadly:
 * {@code onCollaborationRemoved} only touches grants from THAT owner to THAT
 * collaborator (other people's grants on the same notes, and this person's
 * grants from other owners, are untouched), and {@code onNotePermanentlyDeleted}
 * deliberately ignores the event's own {@code ownerId} and drops every grant
 * on the note regardless of who granted it — the class's doc comment notes
 * scoping by owner too would be a no-op today but would silently leak grants
 * the day a note could have more than one owner.
 */
@ExtendWith(MockitoExtension.class)
class NoteShareCleanupListenerTest {

    @Mock private NoteShareRepository noteShareRepository;
    private NoteShareCleanupListener listener;

    @BeforeEach
    void setUp() {
        listener = new NoteShareCleanupListener(noteShareRepository);
    }
    @Test
    void onCollaborationRemoved_deletesOnlyTheGrantsFromThatOwnerToThatCollaborator() {
        listener.onCollaborationRemoved(new CollaborationRemovedEvent(1L, 2L, true));

        verify(noteShareRepository).deleteByOwnerIdAndSharedWithUserId(1L, 2L);
        verifyNoMoreInteractions(noteShareRepository);
    }

    @Test
    void onNotePermanentlyDeleted_deletesEveryGrantOnTheNote_regardlessOfWhoGrantedIt() {
        listener.onNotePermanentlyDeleted(new NotePermanentlyDeletedEvent(5L, 1L));

        // ownerId (1L) is deliberately NOT part of the delete scope - see the
        // class's own doc comment on why that would be a silent no-op today
        // but a latent leak the day a note can have more than one owner.
        verify(noteShareRepository).deleteByNoteId(5L);
        verifyNoMoreInteractions(noteShareRepository);
    }
}
package com.notare.feature.sync.event;

import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.sync.model.NoteTombstone;
import com.notare.feature.sync.repository.NoteTombstoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 12n of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link NoteTombstoneListener}'s source and follows this codebase's
 * existing Mockito conventions exactly (see {@code
 * NoteShareCleanupListenerTest} for the pattern of calling a
 * {@code @TransactionalEventListener}-annotated method directly), but it has
 * not actually been compiled or run. Please run it via {@code mvn test}
 * before trusting it.
 *
 * <p>The class's own javadoc spends most of its length justifying BEFORE_COMMIT
 * over AFTER_COMMIT — that phase choice isn't observable from a unit test
 * calling the method directly, so these tests instead lock in the two things
 * that ARE observable and that the doc comment calls out as deliberate:
 * <ul>
 *   <li>the saved tombstone carries the event's {@code noteId} and
 *       {@code ownerId} exactly — this row is the only surviving evidence
 *       the note ever existed, so a copying mistake here is silent and
 *       permanent;</li>
 *   <li>{@code deletedAt} is set explicitly by the listener to (approximately)
 *       the moment of the call, rather than left for {@code @CreatedDate}
 *       auditing to fill in — the doc comment is explicit that this must be
 *       "the moment of deletion and not whatever a listener ordering happens
 *       to produce".</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NoteTombstoneListenerTest {

    private static final Long NOTE_ID = 42L;
    private static final Long OWNER_ID = 1L;

    @Mock
    private NoteTombstoneRepository noteTombstoneRepository;

    @Captor
    private ArgumentCaptor<NoteTombstone> tombstoneCaptor;
    private NoteTombstoneListener listener;

    @BeforeEach
    void setUp() {
        listener = new NoteTombstoneListener(noteTombstoneRepository);
    }
    @Test
    void onNotePermanentlyDeleted_savesATombstoneCarryingTheNoteAndOwnerIdExactly() {
        listener.onNotePermanentlyDeleted(new NotePermanentlyDeletedEvent(NOTE_ID, OWNER_ID));

        verify(noteTombstoneRepository).save(tombstoneCaptor.capture());
        verifyNoMoreInteractions(noteTombstoneRepository);

        NoteTombstone saved = tombstoneCaptor.getValue();
        assertThat(saved.getNoteId()).isEqualTo(NOTE_ID);
        assertThat(saved.getOwnerId()).isEqualTo(OWNER_ID);
    }

    @Test
    void onNotePermanentlyDeleted_setsDeletedAtExplicitlyToApproximatelyNow() {
        LocalDateTime before = LocalDateTime.now();

        listener.onNotePermanentlyDeleted(new NotePermanentlyDeletedEvent(NOTE_ID, OWNER_ID));

        LocalDateTime after = LocalDateTime.now();
        verify(noteTombstoneRepository).save(tombstoneCaptor.capture());

        NoteTombstone saved = tombstoneCaptor.getValue();
        assertThat(saved.getDeletedAt()).isNotNull();
        // Explicitly set by the listener, not left to auditing — must fall
        // within the call's own execution window.
        assertThat(saved.getDeletedAt()).isBetween(
                before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
    }

    @Test
    void onNotePermanentlyDeleted_differentNotes_eachProducesItsOwnIndependentTombstone() {
        listener.onNotePermanentlyDeleted(new NotePermanentlyDeletedEvent(NOTE_ID, OWNER_ID));
        listener.onNotePermanentlyDeleted(new NotePermanentlyDeletedEvent(43L, OWNER_ID));

        verify(noteTombstoneRepository, org.mockito.Mockito.times(2)).save(tombstoneCaptor.capture());

        var savedTombstones = tombstoneCaptor.getAllValues();
        assertThat(savedTombstones).hasSize(2);
        assertThat(savedTombstones.get(0).getNoteId()).isEqualTo(NOTE_ID);
        assertThat(savedTombstones.get(1).getNoteId()).isEqualTo(43L);
    }
}
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

/** Tests for NoteTombstoneListener. */
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

package com.notare.feature.voiceshare.event;

import com.notare.feature.collaboration.event.CollaborationRemovedEvent;
import com.notare.feature.voicenote.event.VoiceNoteDeletedEvent;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 12i of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link VoiceShareCleanupListener}'s source and follows this codebase's
 * existing Mockito conventions exactly (see {@code
 * NoteShareCleanupListenerTest} for the pattern of calling an
 * event-listener method directly, bypassing Spring's transactional
 * proxying), but it has not actually been compiled or run. Please run it via
 * {@code mvn test} before trusting it.
 *
 * <p>Covers both handlers. There's very little branching in either body —
 * each is a one-line delegation to a repository delete — so what this suite
 * locks in is the pairing the class's own extensive javadoc spends most of
 * its length justifying: {@code onCollaborationRemoved} and {@code
 * onVoiceNoteDeleted} deliberately use DIFFERENT transactional phases
 * ({@code BEFORE_COMMIT} vs. {@code AFTER_COMPLETION} with {@code
 * fallbackExecution = true}), and that divergence is invisible at the unit
 * level (phase is annotation metadata, not method-body behavior) but the
 * SCOPING each delete uses is very much method-body behavior and is what
 * these tests verify:
 * <ul>
 *   <li>{@code onCollaborationRemoved} deletes only the grants from THAT
 *       owner to THAT collaborator — mirroring {@code
 *       NoteShareCleanupListener}'s identical scoping choice for the same
 *       event;</li>
 *   <li>{@code onVoiceNoteDeleted} deletes every grant on the voice note
 *       regardless of who granted it, and deliberately ignores {@code
 *       event.ownerId()} for the delete itself — ownerId isn't needed
 *       because the scoping key is the voice note, not who owns it.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VoiceShareCleanupListenerTest {

    @Mock
    private VoiceShareRepository voiceShareRepository;
    private VoiceShareCleanupListener listener;

    @BeforeEach
    void setUp() {
        listener = new VoiceShareCleanupListener(voiceShareRepository);
    }
    @Test
    void onCollaborationRemoved_deletesOnlyTheGrantsFromThatOwnerToThatCollaborator() {
        listener.onCollaborationRemoved(new CollaborationRemovedEvent(1L, 2L));

        verify(voiceShareRepository).deleteByOwnerIdAndSharedWithUserId(1L, 2L);
        verifyNoMoreInteractions(voiceShareRepository);
    }

    @Test
    void onCollaborationRemoved_doesNotTouchDeleteByVoiceNoteId() {
        listener.onCollaborationRemoved(new CollaborationRemovedEvent(1L, 2L));

        verify(voiceShareRepository, never()).deleteByVoiceNoteId(any());
    }

    @Test
    void onVoiceNoteDeleted_deletesEveryGrantOnTheVoiceNoteRegardlessOfWhoGrantedThem() {
        listener.onVoiceNoteDeleted(new VoiceNoteDeletedEvent(100L, "memo.m4a", 1L));

        verify(voiceShareRepository).deleteByVoiceNoteId(100L);
        verifyNoMoreInteractions(voiceShareRepository);
    }

    @Test
    void onVoiceNoteDeleted_ignoresTheEventsOwnerIdForTheDeleteItself() {
        // The event carries ownerId, but the delete is keyed purely on
        // voiceNoteId — two events differing only in ownerId must produce
        // the identical repository call.
        listener.onVoiceNoteDeleted(new VoiceNoteDeletedEvent(100L, "memo.m4a", 1L));
        listener.onVoiceNoteDeleted(new VoiceNoteDeletedEvent(100L, "memo.m4a", 999L));

        verify(voiceShareRepository, times(2)).deleteByVoiceNoteId(100L);
    }
}
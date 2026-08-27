package com.notare.feature.voiceshare.event;

import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.event.NoteSharedEvent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.service.VoiceShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 12k of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link NoteShareVoicePropagationListener}'s source and follows this
 * codebase's existing Mockito conventions exactly (see {@code
 * NoteShareVoiceAccessSyncListenerTest} for the sibling pattern of using a
 * voice note owned by someone other than the note's owner to catch a
 * regression), but it has not actually been compiled or run. Please run it
 * via {@code mvn test} before trusting it.
 *
 * <p>{@code ShareVoiceNoteRequest} has no generated {@code equals}, so every
 * assertion on the request the listener builds uses an {@code argThat}
 * matcher over its two fields rather than object equality.
 *
 * <p>The center of gravity, per the class's own extensive javadoc:
 * <ul>
 *   <li>no attached voice notes is a true no-op — the listener must not call
 *       {@code voiceShareService} at all;</li>
 *   <li>each grant is made as the RECORDING's own owner
 *       ({@code voiceNote.ownerId()}), never as the note's owner
 *       ({@code event.ownerId()}) — the two coincide for a note owner's own
 *       recording but diverge for a collaborator's, and calling as the wrong
 *       party would 404 in the real service;</li>
 *   <li>the propagation is best-effort per recording: one recording's grant
 *       throwing must not stop the loop from processing the next one, and
 *       must not propagate out of the listener itself (it still runs
 *       BEFORE_COMMIT inside the surrounding note-share transaction).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NoteShareVoicePropagationListenerTest {

    private static final Long NOTE_ID = 42L;
    private static final Long NOTE_OWNER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long COLLABORATOR_ID = 3L; // owns a recording attached to the note, distinct from the note's owner
    private static final Long SHARE_ID = 500L;

    @Mock
    private VoiceNoteService voiceNoteService;

    @Mock
    private VoiceShareService voiceShareService;

    private NoteShareVoicePropagationListener listener;

    @BeforeEach
    void setUp() {
        listener = new NoteShareVoicePropagationListener(voiceNoteService, voiceShareService);
    }

    private VoiceNoteResponse voiceNote(Long id, Long ownerId) {
        return new VoiceNoteResponse(id, ownerId, NOTE_ID, "memo.m4a", "audio/mp4", 2048L, 10, null, null);
    }

    private NoteSharedEvent event(SharePermission permission) {
        return new NoteSharedEvent(SHARE_ID, NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID, permission);
    }

    private boolean matchesGrant(ShareVoiceNoteRequest request, Long sharedWithUserId, SharePermission permission) {
        return request.getSharedWithUserId().equals(sharedWithUserId) && request.getPermission() == permission;
    }

    @Test
    void onNoteShared_noAttachedVoiceNotes_isANoOp() {
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of());

        listener.onNoteShared(event(SharePermission.READ));

        verifyNoInteractions(voiceShareService);
    }

    @Test
    void onNoteShared_recordingOwnedByTheNoteOwner_sharesItAsTheNoteOwner() {
        VoiceNoteResponse ownRecording = voiceNote(100L, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(ownRecording));

        listener.onNoteShared(event(SharePermission.READ));

        verify(voiceShareService).shareVoiceNote(
                eq(NOTE_OWNER_ID),
                eq(100L),
                argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.READ)));
    }

    @Test
    void onNoteShared_recordingOwnedByACollaborator_sharesItAsTheRecordingsOwnerNotTheNotesOwner() {
        // The critical regression case the class's javadoc calls out at length:
        // a WRITE collaborator recorded this reply, so shareVoiceNote must be
        // invoked as the COLLABORATOR (an OWNER operation on their own
        // recording), not as the note's owner — who does not own it and would
        // get a 404 from the real service.
        VoiceNoteResponse collaboratorsRecording = voiceNote(101L, COLLABORATOR_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID))
                .thenReturn(List.of(collaboratorsRecording));

        listener.onNoteShared(event(SharePermission.WRITE));

        verify(voiceShareService).shareVoiceNote(
                eq(COLLABORATOR_ID),
                eq(101L),
                argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.WRITE)));
        verify(voiceShareService, never()).shareVoiceNote(eq(NOTE_OWNER_ID), eq(101L), argThat(req -> true));
    }

    @Test
    void onNoteShared_multipleAttachedRecordings_sharesEachIndependentlyAsItsOwnOwner() {
        VoiceNoteResponse ownRecording = voiceNote(100L, NOTE_OWNER_ID);
        VoiceNoteResponse collaboratorsRecording = voiceNote(101L, COLLABORATOR_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID))
                .thenReturn(List.of(ownRecording, collaboratorsRecording));

        listener.onNoteShared(event(SharePermission.READ));

        verify(voiceShareService).shareVoiceNote(
                eq(NOTE_OWNER_ID), eq(100L), argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.READ)));
        verify(voiceShareService).shareVoiceNote(
                eq(COLLABORATOR_ID), eq(101L), argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.READ)));
        verify(voiceShareService, times(2)).shareVoiceNote(argThat(id -> true), argThat(id -> true), argThat(req -> true));
    }

    @Test
    void onNoteShared_oneRecordingsGrantFails_isSwallowedAndDoesNotStopTheOthers() {
        VoiceNoteResponse failing = voiceNote(100L, NOTE_OWNER_ID);
        VoiceNoteResponse succeeding = voiceNote(101L, COLLABORATOR_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID))
                .thenReturn(List.of(failing, succeeding));
        when(voiceShareService.shareVoiceNote(eq(NOTE_OWNER_ID), eq(100L), argThat(req -> true)))
                .thenThrow(new RuntimeException("not a collaborator of the recipient"));

        // Best-effort per recording, per the class's own doc comment: a single
        // recording's grant failing must not fail the whole handler, since this
        // still runs inside the surrounding NoteShare transaction.
        assertThatCode(() -> listener.onNoteShared(event(SharePermission.READ))).doesNotThrowAnyException();

        verify(voiceShareService).shareVoiceNote(
                eq(COLLABORATOR_ID), eq(101L), argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.READ)));
    }
}
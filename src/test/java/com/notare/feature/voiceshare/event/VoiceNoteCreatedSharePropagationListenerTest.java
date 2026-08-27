package com.notare.feature.voiceshare.event;

import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import com.notare.feature.voicenote.event.VoiceNoteCreatedEvent;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.service.VoiceShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 12l of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link VoiceNoteCreatedSharePropagationListener}'s source and follows this
 * codebase's existing Mockito conventions exactly, but it has not actually
 * been compiled or run. Please run it via {@code mvn test} before trusting
 * it.
 *
 * <p>{@code ShareVoiceNoteRequest} has no generated {@code equals}, so every
 * assertion on the request the listener builds uses an {@code argThat}
 * matcher over its two fields rather than object equality.
 *
 * <p>Covers, per the class's own doc comment:
 * <ul>
 *   <li>a standalone recording ({@code noteId == null}) is a true no-op —
 *       neither the repository nor {@code voiceShareService} is touched at
 *       all, since a standalone voice note has no parent note whose shares
 *       could be inherited;</li>
 *   <li>a note with no existing shares propagates nothing;</li>
 *   <li>each existing {@link NoteShare} on the parent note becomes a grant
 *       on the new recording, made AS THE VOICE NOTE'S OWNER (always
 *       {@code event.ownerId()} here — this listener fires right after that
 *       owner's own upload, unlike 12k's per-recording-owner nuance) and
 *       carrying the matching {@code sharedWithUserId}/{@code permission}
 *       pair from that note share;</li>
 *   <li>the repository is queried with the class's fixed page size
 *       constant, not an unbounded fetch;</li>
 *   <li>one recipient's grant failing is swallowed silently and does not
 *       stop propagation to the others — the new recording has already been
 *       successfully persisted by the time this runs, and a propagation
 *       failure must not make it disappear from the owner's note.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VoiceNoteCreatedSharePropagationListenerTest {

    private static final Long NOTE_ID = 42L;
    private static final Long OWNER_ID = 1L;
    private static final Long VOICE_NOTE_ID = 200L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long OTHER_RECIPIENT_ID = 3L;

    @Mock
    private NoteShareRepository noteShareRepository;

    @Mock
    private VoiceShareService voiceShareService;

    private VoiceNoteCreatedSharePropagationListener listener;

    @BeforeEach
    void setUp() {
        listener = new VoiceNoteCreatedSharePropagationListener(noteShareRepository, voiceShareService);
    }

    private VoiceNoteCreatedEvent event(Long noteId) {
        return new VoiceNoteCreatedEvent(VOICE_NOTE_ID, OWNER_ID, noteId);
    }

    private NoteShare noteShare(Long sharedWithUserId, SharePermission permission) {
        NoteShare s = new NoteShare();
        s.setNoteId(NOTE_ID);
        s.setOwnerId(OWNER_ID);
        s.setSharedWithUserId(sharedWithUserId);
        s.setPermission(permission);
        return s;
    }

    private boolean matchesGrant(ShareVoiceNoteRequest request, Long sharedWithUserId, SharePermission permission) {
        return request.getSharedWithUserId().equals(sharedWithUserId) && request.getPermission() == permission;
    }

    @Test
    void onVoiceNoteCreated_standaloneRecording_noteIdIsNull_isANoOp() {
        listener.onVoiceNoteCreated(event(null));

        verifyNoInteractions(noteShareRepository);
        verifyNoInteractions(voiceShareService);
    }

    @Test
    void onVoiceNoteCreated_parentNoteHasNoExistingShares_propagatesNothing() {
        when(noteShareRepository.findByNoteIdAndOwnerId(eq(NOTE_ID), eq(OWNER_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));

        listener.onVoiceNoteCreated(event(NOTE_ID));

        verifyNoInteractions(voiceShareService);
    }

    @Test
    void onVoiceNoteCreated_queriesWithTheFixedPageSizeConstant() {
        when(noteShareRepository.findByNoteIdAndOwnerId(NOTE_ID, OWNER_ID, PageRequest.of(0, 500)))
                .thenReturn(new PageImpl<>(List.of()));

        listener.onVoiceNoteCreated(event(NOTE_ID));

        verify(noteShareRepository).findByNoteIdAndOwnerId(NOTE_ID, OWNER_ID, PageRequest.of(0, 500));
    }

    @Test
    void onVoiceNoteCreated_oneExistingNoteShare_propagatesAMatchingGrantAsTheOwner() {
        Page<NoteShare> shares = new PageImpl<>(List.of(noteShare(RECIPIENT_ID, SharePermission.WRITE)));
        when(noteShareRepository.findByNoteIdAndOwnerId(NOTE_ID, OWNER_ID, PageRequest.of(0, 500)))
                .thenReturn(shares);

        listener.onVoiceNoteCreated(event(NOTE_ID));

        verify(voiceShareService).shareVoiceNote(
                eq(OWNER_ID),
                eq(VOICE_NOTE_ID),
                argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.WRITE)));
    }

    @Test
    void onVoiceNoteCreated_multipleExistingShares_propagatesToEachRecipientIndependently() {
        Page<NoteShare> shares = new PageImpl<>(List.of(
                noteShare(RECIPIENT_ID, SharePermission.READ),
                noteShare(OTHER_RECIPIENT_ID, SharePermission.WRITE)));
        when(noteShareRepository.findByNoteIdAndOwnerId(NOTE_ID, OWNER_ID, PageRequest.of(0, 500)))
                .thenReturn(shares);

        listener.onVoiceNoteCreated(event(NOTE_ID));

        verify(voiceShareService).shareVoiceNote(
                eq(OWNER_ID), eq(VOICE_NOTE_ID), argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.READ)));
        verify(voiceShareService).shareVoiceNote(
                eq(OWNER_ID), eq(VOICE_NOTE_ID), argThat(req -> matchesGrant(req, OTHER_RECIPIENT_ID, SharePermission.WRITE)));
        verify(voiceShareService, times(2)).shareVoiceNote(eq(OWNER_ID), eq(VOICE_NOTE_ID), argThat(req -> true));
    }

    @Test
    void onVoiceNoteCreated_oneRecipientsGrantFails_isSwallowedAndDoesNotStopTheOthers() {
        Page<NoteShare> shares = new PageImpl<>(List.of(
                noteShare(RECIPIENT_ID, SharePermission.READ),
                noteShare(OTHER_RECIPIENT_ID, SharePermission.WRITE)));
        when(noteShareRepository.findByNoteIdAndOwnerId(NOTE_ID, OWNER_ID, PageRequest.of(0, 500)))
                .thenReturn(shares);
        when(voiceShareService.shareVoiceNote(
                eq(OWNER_ID), eq(VOICE_NOTE_ID), argThat(req -> matchesGrant(req, RECIPIENT_ID, SharePermission.READ))))
                .thenThrow(new RuntimeException("duplicate grant"));

        // The recording has already been successfully created by this point —
        // a propagation failure must not surface out of the listener.
        assertThatCode(() -> listener.onVoiceNoteCreated(event(NOTE_ID))).doesNotThrowAnyException();

        verify(voiceShareService).shareVoiceNote(
                eq(OWNER_ID), eq(VOICE_NOTE_ID), argThat(req -> matchesGrant(req, OTHER_RECIPIENT_ID, SharePermission.WRITE)));
    }
}
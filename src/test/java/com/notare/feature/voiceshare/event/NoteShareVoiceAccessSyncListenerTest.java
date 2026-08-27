package com.notare.feature.voiceshare.event;

import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.event.NoteSharePermissionChangedEvent;
import com.notare.feature.share.event.NoteShareRevokedEvent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.model.VoiceShare;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 12j of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link NoteShareVoiceAccessSyncListener}'s source and follows this
 * codebase's existing Mockito conventions exactly, but it has not actually
 * been compiled or run. Please run it via {@code mvn test} before trusting
 * it.
 *
 * <p>Covers both handlers, with the center of gravity on the fact the
 * class's own javadoc spends most of its length explaining: both lookups
 * key on {@code voiceNote.ownerId()} — the RECORDING's own owner — not
 * {@code event.ownerId()} (the note's owner). Several tests here use a
 * voice note owned by someone OTHER than the note's owner (a WRITE
 * collaborator's own reply) specifically to catch a regression back to
 * {@code event.ownerId()}, which the javadoc says would silently stop
 * tracking permission changes for exactly that recording.
 *
 * <ul>
 *   <li>{@code onNoteSharePermissionChanged}: updates a found share's
 *       permission and saves it; is a no-op (no save) when the permission
 *       already matches; is a no-op (but not an error, per the "expected,
 *       not an error" doc comment) when no matching share exists for a
 *       recording; and processes every attached recording independently —
 *       one recording's missing share doesn't stop the loop from updating
 *       another;</li>
 *   <li>{@code onNoteShareRevoked}: deletes a found share; does nothing
 *       when none exists; and, same as the permission-changed handler,
 *       keys the lookup on the recording's own owner.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NoteShareVoiceAccessSyncListenerTest {

    private static final Long NOTE_ID = 42L;
    private static final Long NOTE_OWNER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long COLLABORATOR_ID = 3L; // owns a recording attached to the note, distinct from the note's owner
    private static final Long VOICE_NOTE_ID = 100L;
    private static final Long SHARE_ID = 500L;

    @Mock
    private VoiceNoteService voiceNoteService;

    @Mock
    private VoiceShareRepository voiceShareRepository;

    private NoteShareVoiceAccessSyncListener listener;

    @BeforeEach
    void setUp() {
        listener = new NoteShareVoiceAccessSyncListener(voiceNoteService, voiceShareRepository);
    }

    private VoiceNoteResponse voiceNote(Long id, Long ownerId) {
        return new VoiceNoteResponse(id, ownerId, NOTE_ID, "memo.m4a", "audio/mp4", 2048L, 10, null, null);
    }

    private VoiceShare share(SharePermission permission) {
        VoiceShare s = new VoiceShare();
        s.setId(SHARE_ID);
        s.setVoiceNoteId(VOICE_NOTE_ID);
        s.setOwnerId(NOTE_OWNER_ID);
        s.setSharedWithUserId(RECIPIENT_ID);
        s.setPermission(permission);
        return s;
    }

    // -------------------------------------------------------------------
    // onNoteSharePermissionChanged
    // -------------------------------------------------------------------

    @Test
    void onNoteSharePermissionChanged_matchingShareWithDifferentPermission_updatesAndSaves() {
        VoiceNoteResponse vn = voiceNote(VOICE_NOTE_ID, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(vn));
        VoiceShare existing = share(SharePermission.READ);
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));

        listener.onNoteSharePermissionChanged(new NoteSharePermissionChangedEvent(
                SHARE_ID, NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID,
                SharePermission.READ, SharePermission.WRITE));

        assertThat(existing.getPermission()).isEqualTo(SharePermission.WRITE);
        verify(voiceShareRepository).save(existing);
    }

    @Test
    void onNoteSharePermissionChanged_permissionAlreadyMatches_isANoOp() {
        VoiceNoteResponse vn = voiceNote(VOICE_NOTE_ID, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(vn));
        VoiceShare existing = share(SharePermission.WRITE);
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));

        listener.onNoteSharePermissionChanged(new NoteSharePermissionChangedEvent(
                SHARE_ID, NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID,
                SharePermission.WRITE, SharePermission.WRITE));

        verify(voiceShareRepository, never()).save(any());
    }

    @Test
    void onNoteSharePermissionChanged_noMatchingShare_isSkippedWithoutError() {
        VoiceNoteResponse vn = voiceNote(VOICE_NOTE_ID, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(vn));
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        listener.onNoteSharePermissionChanged(new NoteSharePermissionChangedEvent(
                SHARE_ID, NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID,
                SharePermission.READ, SharePermission.WRITE));

        verify(voiceShareRepository, never()).save(any());
    }

    @Test
    void onNoteSharePermissionChanged_recordingOwnedByACollaborator_looksUpByTheRecordingsOwnerNotTheNotesOwner() {
        // The critical regression case the class's javadoc calls out: a
        // WRITE collaborator recorded this reply, so the VoiceShare row's
        // ownerId is the COLLABORATOR's id, not the note owner's. The
        // lookup must use voiceNote.ownerId() (the collaborator) or it will
        // never find this share.
        VoiceNoteResponse collaboratorsRecording = voiceNote(VOICE_NOTE_ID, COLLABORATOR_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID))
                .thenReturn(List.of(collaboratorsRecording));
        VoiceShare existing = share(SharePermission.READ);
        existing.setOwnerId(COLLABORATOR_ID);
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, COLLABORATOR_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));

        listener.onNoteSharePermissionChanged(new NoteSharePermissionChangedEvent(
                SHARE_ID, NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID,
                SharePermission.READ, SharePermission.WRITE));

        assertThat(existing.getPermission()).isEqualTo(SharePermission.WRITE);
        verify(voiceShareRepository).save(existing);
        // Must NOT have looked it up under the note owner's id.
        verify(voiceShareRepository, never()).findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID);
    }

    @Test
    void onNoteSharePermissionChanged_multipleAttachedRecordings_processesEachIndependently() {
        VoiceNoteResponse first = voiceNote(VOICE_NOTE_ID, NOTE_OWNER_ID);
        VoiceNoteResponse second = voiceNote(101L, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(first, second));

        // First recording has no share yet (skipped); second has one that needs updating.
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());
        VoiceShare secondShare = share(SharePermission.READ);
        secondShare.setVoiceNoteId(101L);
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                101L, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(secondShare));

        listener.onNoteSharePermissionChanged(new NoteSharePermissionChangedEvent(
                SHARE_ID, NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID,
                SharePermission.READ, SharePermission.WRITE));

        assertThat(secondShare.getPermission()).isEqualTo(SharePermission.WRITE);
        verify(voiceShareRepository).save(secondShare);
        verify(voiceShareRepository, never()).save(argThat((VoiceShare s) -> !s.getVoiceNoteId().equals(101L)));
    }

    // -------------------------------------------------------------------
    // onNoteShareRevoked
    // -------------------------------------------------------------------

    @Test
    void onNoteShareRevoked_matchingShare_isDeleted() {
        VoiceNoteResponse vn = voiceNote(VOICE_NOTE_ID, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(vn));
        VoiceShare existing = share(SharePermission.READ);
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));

        listener.onNoteShareRevoked(new NoteShareRevokedEvent(NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID));

        verify(voiceShareRepository).delete(existing);
    }

    @Test
    void onNoteShareRevoked_noMatchingShare_doesNothing() {
        VoiceNoteResponse vn = voiceNote(VOICE_NOTE_ID, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(vn));
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        listener.onNoteShareRevoked(new NoteShareRevokedEvent(NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID));

        verify(voiceShareRepository, never()).delete(any());
    }

    @Test
    void onNoteShareRevoked_recordingOwnedByACollaborator_looksUpByTheRecordingsOwnerNotTheNotesOwner() {
        VoiceNoteResponse collaboratorsRecording = voiceNote(VOICE_NOTE_ID, COLLABORATOR_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID))
                .thenReturn(List.of(collaboratorsRecording));
        VoiceShare existing = share(SharePermission.READ);
        existing.setOwnerId(COLLABORATOR_ID);
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, COLLABORATOR_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));

        listener.onNoteShareRevoked(new NoteShareRevokedEvent(NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID));

        verify(voiceShareRepository).delete(existing);
        verify(voiceShareRepository, never()).findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID);
    }

    @Test
    void onNoteShareRevoked_multipleAttachedRecordings_deletesEachMatchingGrantIndependently() {
        VoiceNoteResponse first = voiceNote(VOICE_NOTE_ID, NOTE_OWNER_ID);
        VoiceNoteResponse second = voiceNote(101L, NOTE_OWNER_ID);
        when(voiceNoteService.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(List.of(first, second));

        VoiceShare firstShare = share(SharePermission.READ);
        VoiceShare secondShare = share(SharePermission.WRITE);
        secondShare.setVoiceNoteId(101L);
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                VOICE_NOTE_ID, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(firstShare));
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                101L, NOTE_OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(secondShare));

        listener.onNoteShareRevoked(new NoteShareRevokedEvent(NOTE_ID, "Q3 Plan", NOTE_OWNER_ID, RECIPIENT_ID));

        verify(voiceShareRepository).delete(firstShare);
        verify(voiceShareRepository).delete(secondShare);
    }
}
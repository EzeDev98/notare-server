package com.notare.feature.voiceshare.mapper;

import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voiceshare.dto.response.SharedVoiceNoteSummaryResponse;
import com.notare.feature.voiceshare.dto.response.VoiceShareResponse;
import com.notare.feature.voiceshare.model.VoiceShare;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3g of the coverage roadmap.
 *
 * <p>Covers {@link VoiceShareMapper#toResponse} and {@link
 * VoiceShareMapper#toSharedVoiceNote} - the voice-note mirror of Step 3b's
 * {@code NoteShareMapper}, but FLATTENED rather than nested (per {@link
 * SharedVoiceNoteSummaryResponse}'s own doc comment), which is exactly what
 * this test exists to pin down: {@code toSharedVoiceNote} pulls from TWO
 * different sources into one flat record, and the one field most likely to
 * get pulled from the wrong one is {@code ownerId} - both the grant and the
 * voice note carry an owner, and they happen to be the same person in real
 * data, which would hide a transposition. This test deliberately gives them
 * DIFFERENT values so a mistake there fails loudly instead of coincidentally
 * passing.
 */
class VoiceShareMapperTest {

    private final VoiceShareMapper mapper = new VoiceShareMapper();

    @Test
    void toResponse_mapsEveryFieldFromTheGrant() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 1, 2, 11, 0);

        VoiceShare share = new VoiceShare();
        share.setId(1L);
        share.setVoiceNoteId(2L);
        share.setOwnerId(3L);
        share.setSharedWithUserId(4L);
        share.setPermission(SharePermission.WRITE);
        share.setCreatedAt(created);
        share.setUpdatedAt(updated);

        VoiceShareResponse response = mapper.toResponse(share);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.voiceNoteId()).isEqualTo(2L);
        assertThat(response.ownerId()).isEqualTo(3L);
        assertThat(response.sharedWithUserId()).isEqualTo(4L);
        assertThat(response.permission()).isEqualTo(SharePermission.WRITE);
    }

    @Test
    void toSharedVoiceNote_pullsOwnerIdFromTheGrantNotTheVoiceNote() {
        // Deliberately different values on the grant's ownerId (3) vs the
        // voice note's own ownerId (3, unused here) - the field the response
        // exposes is documented as "who shared it with me," which is the
        // GRANT's owner. Using the same value on both would let a
        // transposition bug pass unnoticed.
        VoiceShare share = new VoiceShare();
        share.setId(1L);
        share.setOwnerId(3L);
        share.setPermission(SharePermission.READ);
        share.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        VoiceNoteResponse voiceNote = new VoiceNoteResponse(
                2L, 3L, 99L, "memo.m4a", "audio/mp4", 1024L, 30,
                LocalDateTime.of(2025, 6, 1, 0, 0), null);

        SharedVoiceNoteSummaryResponse result = mapper.toSharedVoiceNote(share, voiceNote);

        assertThat(result.ownerId()).isEqualTo(3L);
    }

    @Test
    void toSharedVoiceNote_voiceNoteIdAndNoteIdComeFromTheVoiceNoteNotTheGrant() {
        VoiceShare share = new VoiceShare();
        share.setId(1L);
        share.setPermission(SharePermission.READ);
        share.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        VoiceNoteResponse voiceNote = new VoiceNoteResponse(
                2L, 3L, 99L, "memo.m4a", "audio/mp4", 1024L, 30,
                LocalDateTime.of(2025, 6, 1, 0, 0), null);

        SharedVoiceNoteSummaryResponse result = mapper.toSharedVoiceNote(share, voiceNote);

        assertThat(result.voiceNoteId()).isEqualTo(2L);
        assertThat(result.noteId()).isEqualTo(99L);
    }

    @Test
    void toSharedVoiceNote_createdAtIsTheUploadTimeNotTheShareTime() {
        // The record's own doc comment distinguishes these explicitly:
        // createdAt = when the voice note was uploaded, sharedAt = when the
        // grant was made. Different sources, easy to swap.
        LocalDateTime uploadedAt = LocalDateTime.of(2025, 6, 1, 0, 0);
        LocalDateTime sharedAt = LocalDateTime.of(2026, 1, 1, 10, 0);

        VoiceShare share = new VoiceShare();
        share.setId(1L);
        share.setPermission(SharePermission.READ);
        share.setCreatedAt(sharedAt);

        VoiceNoteResponse voiceNote = new VoiceNoteResponse(
                2L, 3L, null, "memo.m4a", "audio/mp4", 1024L, 30, uploadedAt, null);

        SharedVoiceNoteSummaryResponse result = mapper.toSharedVoiceNote(share, voiceNote);

        assertThat(result.createdAt()).isEqualTo(uploadedAt);
        assertThat(result.sharedAt()).isEqualTo(sharedAt);
        assertThat(result.createdAt()).isNotEqualTo(result.sharedAt());
    }

    @Test
    void toSharedVoiceNote_mapsFileMetadataFromTheVoiceNote() {
        VoiceShare share = new VoiceShare();
        share.setId(1L);
        share.setPermission(SharePermission.READ);
        share.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        VoiceNoteResponse voiceNote = new VoiceNoteResponse(
                2L, 3L, null, "memo.m4a", "audio/mp4", 1024L, 30,
                LocalDateTime.of(2025, 6, 1, 0, 0), null);

        SharedVoiceNoteSummaryResponse result = mapper.toSharedVoiceNote(share, voiceNote);

        assertThat(result.originalFilename()).isEqualTo("memo.m4a");
        assertThat(result.contentType()).isEqualTo("audio/mp4");
        assertThat(result.sizeBytes()).isEqualTo(1024L);
        assertThat(result.durationSeconds()).isEqualTo(30);
    }
}
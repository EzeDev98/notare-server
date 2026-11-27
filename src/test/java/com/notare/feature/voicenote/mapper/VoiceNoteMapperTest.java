package com.notare.feature.voicenote.mapper;

import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.model.VoiceNote;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for VoiceNoteMapper. */
class VoiceNoteMapperTest {

    private final VoiceNoteMapper mapper = new VoiceNoteMapper();

    @Test
    void toEntity_mapsEveryArgumentWithoutTransposingSizeAndDuration() {
        VoiceNote entity = mapper.toEntity(1L, 2L, "memo.m4a", "audio/mp4", "key/1.m4a", 2048L, 45);

        assertThat(entity.getOwnerId()).isEqualTo(1L);
        assertThat(entity.getNoteId()).isEqualTo(2L);
        assertThat(entity.getOriginalFilename()).isEqualTo("memo.m4a");
        assertThat(entity.getContentType()).isEqualTo("audio/mp4");
        assertThat(entity.getStorageKey()).isEqualTo("key/1.m4a");
        assertThat(entity.getSizeBytes()).isEqualTo(2048L);
        assertThat(entity.getDurationSeconds()).isEqualTo(45);
    }

    @Test
    void toEntity_standaloneRecordingHasANullNoteId() {
        VoiceNote entity = mapper.toEntity(1L, null, "memo.m4a", "audio/mp4", "key/1.m4a", 2048L, null);

        assertThat(entity.getNoteId()).isNull();
        assertThat(entity.getDurationSeconds()).isNull();
    }

    @Test
    void toResponse_mapsEveryFieldWithoutTransposingSizeAndDuration() {
        VoiceNote entity = mapper.toEntity(1L, 2L, "memo.m4a", "audio/mp4", "key/1.m4a", 2048L, 45);
        entity.setId(9L);
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 1, 2, 0, 0);
        entity.setCreatedAt(created);
        entity.setUpdatedAt(updated);

        VoiceNoteResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.ownerId()).isEqualTo(1L);
        assertThat(response.noteId()).isEqualTo(2L);
        assertThat(response.originalFilename()).isEqualTo("memo.m4a");
        assertThat(response.contentType()).isEqualTo("audio/mp4");
        assertThat(response.sizeBytes()).isEqualTo(2048L);
        assertThat(response.durationSeconds()).isEqualTo(45);
        assertThat(response.createdAt()).isEqualTo(created);
        assertThat(response.updatedAt()).isEqualTo(updated);
    }

    @Test
    void toResponse_standaloneRecordingCarriesANullNoteIdThrough() {
        VoiceNote entity = mapper.toEntity(1L, null, "memo.m4a", "audio/mp4", "key/1.m4a", 2048L, null);

        VoiceNoteResponse response = mapper.toResponse(entity);

        assertThat(response.noteId()).isNull();
        assertThat(response.durationSeconds()).isNull();
    }
}

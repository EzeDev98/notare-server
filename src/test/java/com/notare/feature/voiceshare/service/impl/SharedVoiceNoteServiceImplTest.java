package com.notare.feature.voiceshare.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.VoiceShareNotFoundException;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.dto.response.SharedVoiceNoteSummaryResponse;
import com.notare.feature.voiceshare.mapper.VoiceShareMapper;
import com.notare.feature.voiceshare.model.VoiceShare;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level, mocking every collaborator — mirrors VoiceShareServiceImplTest's
 * convention. Covers the roadmap's four scenarios: recipient sees shared
 * voice notes; non-recipient cannot; READ recipient can access audio;
 * revoked recipient loses access.
 */
@ExtendWith(MockitoExtension.class)
class SharedVoiceNoteServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long STRANGER_ID = 99L;
    private static final Long VOICE_NOTE_ID = 100L;

    @Mock
    private VoiceShareRepository voiceShareRepository;

    @Mock
    private VoiceShareMapper voiceShareMapper;

    @Mock
    private VoiceNoteService voiceNoteService;

    private SharedVoiceNoteServiceImpl service;

    private VoiceNoteResponse voiceNote;
    private VoiceShare grant;

    @BeforeEach
    void setUp() {
        service = new SharedVoiceNoteServiceImpl(voiceShareRepository, voiceShareMapper, voiceNoteService);

        voiceNote = new VoiceNoteResponse(
                VOICE_NOTE_ID, OWNER_ID, null, "memo.m4a", "audio/mp4",
                1024L, 30, LocalDateTime.now(), null);

        grant = new VoiceShare();
        grant.setId(10L);
        grant.setVoiceNoteId(VOICE_NOTE_ID);
        grant.setOwnerId(OWNER_ID);
        grant.setSharedWithUserId(RECIPIENT_ID);
        grant.setPermission(SharePermission.READ);
    }

    @Test
    void listSharedWithMe_recipientSeesSharedVoiceNotes() {
        Pageable pageable = PageRequest.of(0, 20);
        when(voiceShareRepository.findBySharedWithUserId(RECIPIENT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(grant), pageable, 1));
        when(voiceNoteService.getAuthorizedVoiceNoteSummaries(anyList()))
                .thenReturn(Map.of(VOICE_NOTE_ID, voiceNote));

        SharedVoiceNoteSummaryResponse summary = new SharedVoiceNoteSummaryResponse(
                grant.getId(), VOICE_NOTE_ID, null, OWNER_ID, SharePermission.READ,
                "memo.m4a", "audio/mp4", 1024L, 30, LocalDateTime.now(), grant.getCreatedAt());
        when(voiceShareMapper.toSharedVoiceNote(grant, voiceNote)).thenReturn(summary);

        PagedResponse<SharedVoiceNoteSummaryResponse> result = service.listSharedWithMe(RECIPIENT_ID, pageable);

        assertThat(result.content()).containsExactly(summary);
    }

    @Test
    void listSharedWithMe_dropsGrantsWhoseVoiceNoteIsGone() {
        Pageable pageable = PageRequest.of(0, 20);
        when(voiceShareRepository.findBySharedWithUserId(RECIPIENT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(grant), pageable, 1));
        // Voice note absent from the batch-resolved map -> deleted since the
        // grant was made. The row must be dropped, not rendered with a hole.
        when(voiceNoteService.getAuthorizedVoiceNoteSummaries(anyList())).thenReturn(Map.of());

        PagedResponse<SharedVoiceNoteSummaryResponse> result = service.listSharedWithMe(RECIPIENT_ID, pageable);

        assertThat(result.content()).isEmpty();
        verify(voiceShareMapper, org.mockito.Mockito.never()).toSharedVoiceNote(any(), any());
    }

    @Test
    void getAudio_readRecipientCanAccess() {
        when(voiceShareRepository.findByVoiceNoteIdAndSharedWithUserId(VOICE_NOTE_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(grant));

        Resource resource = mock(Resource.class);
        VoiceNoteContent content = new VoiceNoteContent(resource, "audio/mp4", "memo.m4a", 1024L);
        when(voiceNoteService.getAuthorizedAudio(VOICE_NOTE_ID)).thenReturn(content);

        VoiceNoteContent result = service.getAudio(RECIPIENT_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(content);
    }

    @Test
    void getAudio_nonRecipientCannotAccess() {
        // No grant exists for the stranger -> 404-equivalent, same as an
        // owner's note not existing. The voice note's existence is never
        // confirmed to someone with no grant.
        when(voiceShareRepository.findByVoiceNoteIdAndSharedWithUserId(VOICE_NOTE_ID, STRANGER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAudio(STRANGER_ID, VOICE_NOTE_ID))
                .isInstanceOf(VoiceShareNotFoundException.class);

        verify(voiceNoteService, org.mockito.Mockito.never()).getAuthorizedAudio(any());
    }

    @Test
    void getAudio_revokedRecipientLosesAccess() {
        // Revocation deletes the VoiceShare row entirely (VoiceShareServiceImpl
        // #revoke), so from this service's perspective a revoked recipient looks
        // identical to one who was never granted access: no row, 404-equivalent.
        when(voiceShareRepository.findByVoiceNoteIdAndSharedWithUserId(VOICE_NOTE_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAudio(RECIPIENT_ID, VOICE_NOTE_ID))
                .isInstanceOf(VoiceShareNotFoundException.class);

        verify(voiceNoteService, org.mockito.Mockito.never()).getAuthorizedAudio(any());
    }

    @Test
    void getSharedVoiceNote_readRecipientCanAccessMetadata() {
        when(voiceShareRepository.findByVoiceNoteIdAndSharedWithUserId(VOICE_NOTE_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(grant));
        when(voiceNoteService.getAuthorizedVoiceNote(VOICE_NOTE_ID)).thenReturn(voiceNote);

        SharedVoiceNoteSummaryResponse summary = new SharedVoiceNoteSummaryResponse(
                grant.getId(), VOICE_NOTE_ID, null, OWNER_ID, SharePermission.READ,
                "memo.m4a", "audio/mp4", 1024L, 30, LocalDateTime.now(), grant.getCreatedAt());
        when(voiceShareMapper.toSharedVoiceNote(grant, voiceNote)).thenReturn(summary);

        SharedVoiceNoteSummaryResponse result = service.getSharedVoiceNote(RECIPIENT_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(summary);
    }
}

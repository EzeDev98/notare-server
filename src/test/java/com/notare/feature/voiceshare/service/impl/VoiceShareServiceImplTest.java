package com.notare.feature.voiceshare.service.impl;

import com.notare.common.exception.InvalidVoiceShareException;
import com.notare.common.exception.VoiceAlreadySharedException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.exception.VoiceShareNotFoundException;
import com.notare.feature.collaboration.service.CollaborationService;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.dto.request.UpdateVoiceSharePermissionRequest;
import com.notare.feature.voiceshare.dto.response.VoiceShareResponse;
import com.notare.feature.voiceshare.event.VoiceNoteSharedEvent;
import com.notare.feature.voiceshare.event.VoiceShareRevokedEvent;
import com.notare.feature.voiceshare.event.VoiceSharePermissionChangedEvent;
import com.notare.feature.voiceshare.mapper.VoiceShareMapper;
import com.notare.feature.voiceshare.model.VoiceShare;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level, mocking every collaborator — same convention this codebase
 * establishes for the module (no existing note-share test to mirror, so this
 * follows the general Spring Boot + JUnit5 + Mockito style spring-boot-starter-test
 * already provides). Covers the six scenarios called out by the roadmap:
 * owner can share; duplicate share rejected; non-owner cannot share; owner
 * can update permission; owner can revoke; sharing requires an accepted
 * collaboration.
 */
@ExtendWith(MockitoExtension.class)
class VoiceShareServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long VOICE_NOTE_ID = 100L;

    @Mock
    private VoiceShareRepository voiceShareRepository;

    @Mock
    private VoiceShareMapper voiceShareMapper;

    @Mock
    private VoiceNoteService voiceNoteService;

    @Mock
    private CollaborationService collaborationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private VoiceShareServiceImpl service;

    private VoiceNoteResponse voiceNote;

    @BeforeEach
    void setUp() {
        service = new VoiceShareServiceImpl(
                voiceShareRepository, voiceShareMapper, voiceNoteService, collaborationService, eventPublisher);

        voiceNote = new VoiceNoteResponse(
                VOICE_NOTE_ID, OWNER_ID, null, "memo.m4a", "audio/mp4",
                1024L, 30, LocalDateTime.now(), null);
    }

    @Test
    void shareVoiceNote_ownerCanShare() {
        ShareVoiceNoteRequest request = new ShareVoiceNoteRequest();
        request.setSharedWithUserId(RECIPIENT_ID);
        request.setPermission(SharePermission.READ);

        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(voiceNote);
        when(collaborationService.isAcceptedCollaborator(OWNER_ID, RECIPIENT_ID)).thenReturn(true);
        when(voiceShareRepository.existsByVoiceNoteIdAndSharedWithUserId(VOICE_NOTE_ID, RECIPIENT_ID)).thenReturn(false);

        VoiceShare saved = new VoiceShare();
        saved.setId(10L);
        saved.setVoiceNoteId(VOICE_NOTE_ID);
        saved.setOwnerId(OWNER_ID);
        saved.setSharedWithUserId(RECIPIENT_ID);
        saved.setPermission(SharePermission.READ);
        when(voiceShareRepository.save(any(VoiceShare.class))).thenReturn(saved);

        VoiceShareResponse expectedResponse = new VoiceShareResponse(
                10L, VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID, SharePermission.READ, LocalDateTime.now(), null);
        when(voiceShareMapper.toResponse(saved)).thenReturn(expectedResponse);

        VoiceShareResponse result = service.shareVoiceNote(OWNER_ID, VOICE_NOTE_ID, request);

        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<VoiceNoteSharedEvent> eventCaptor = ArgumentCaptor.forClass(VoiceNoteSharedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        VoiceNoteSharedEvent published = eventCaptor.getValue();
        assertThat(published.voiceNoteId()).isEqualTo(VOICE_NOTE_ID);
        assertThat(published.sharedWithUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(published.ownerId()).isEqualTo(OWNER_ID);
        assertThat(published.voiceNoteTitle()).isEqualTo("memo.m4a");
    }

    @Test
    void shareVoiceNote_duplicateShareRejected_byPreCheck() {
        ShareVoiceNoteRequest request = new ShareVoiceNoteRequest();
        request.setSharedWithUserId(RECIPIENT_ID);
        request.setPermission(SharePermission.READ);

        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(voiceNote);
        when(collaborationService.isAcceptedCollaborator(OWNER_ID, RECIPIENT_ID)).thenReturn(true);
        when(voiceShareRepository.existsByVoiceNoteIdAndSharedWithUserId(VOICE_NOTE_ID, RECIPIENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.shareVoiceNote(OWNER_ID, VOICE_NOTE_ID, request))
                .isInstanceOf(VoiceAlreadySharedException.class);

        verify(voiceShareRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shareVoiceNote_duplicateShareRejected_byRaceOnConstraint() {
        ShareVoiceNoteRequest request = new ShareVoiceNoteRequest();
        request.setSharedWithUserId(RECIPIENT_ID);
        request.setPermission(SharePermission.READ);

        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(voiceNote);
        when(collaborationService.isAcceptedCollaborator(OWNER_ID, RECIPIENT_ID)).thenReturn(true);
        when(voiceShareRepository.existsByVoiceNoteIdAndSharedWithUserId(VOICE_NOTE_ID, RECIPIENT_ID)).thenReturn(false);
        when(voiceShareRepository.save(any(VoiceShare.class)))
                .thenThrow(new DataIntegrityViolationException("uq_voice_shares_voice_note_user"));

        assertThatThrownBy(() -> service.shareVoiceNote(OWNER_ID, VOICE_NOTE_ID, request))
                .isInstanceOf(VoiceAlreadySharedException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shareVoiceNote_nonOwnerCannotShare() {
        ShareVoiceNoteRequest request = new ShareVoiceNoteRequest();
        request.setSharedWithUserId(RECIPIENT_ID);
        request.setPermission(SharePermission.READ);

        // getVoiceNote is owner-scoped: a non-owner probing someone else's
        // voice note id gets 404 from the voicenote module itself.
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        assertThatThrownBy(() -> service.shareVoiceNote(OWNER_ID, VOICE_NOTE_ID, request))
                .isInstanceOf(VoiceNoteNotFoundException.class);

        verify(collaborationService, never()).isAcceptedCollaborator(any(), any());
        verify(voiceShareRepository, never()).save(any());
    }

    @Test
    void shareVoiceNote_selfShareRejected_withoutQuerying() {
        ShareVoiceNoteRequest request = new ShareVoiceNoteRequest();
        request.setSharedWithUserId(OWNER_ID);
        request.setPermission(SharePermission.READ);

        assertThatThrownBy(() -> service.shareVoiceNote(OWNER_ID, VOICE_NOTE_ID, request))
                .isInstanceOf(InvalidVoiceShareException.class);

        // Gate 1 (self-share) is pure in-memory: no query should have run at all.
        verify(voiceNoteService, never()).getVoiceNote(any(), any());
    }

    @Test
    void shareVoiceNote_requiresAcceptedCollaboration() {
        ShareVoiceNoteRequest request = new ShareVoiceNoteRequest();
        request.setSharedWithUserId(RECIPIENT_ID);
        request.setPermission(SharePermission.READ);

        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(voiceNote);
        when(collaborationService.isAcceptedCollaborator(OWNER_ID, RECIPIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.shareVoiceNote(OWNER_ID, VOICE_NOTE_ID, request))
                .isInstanceOf(InvalidVoiceShareException.class);

        verify(voiceShareRepository, never()).existsByVoiceNoteIdAndSharedWithUserId(any(), any());
        verify(voiceShareRepository, never()).save(any());
    }

    @Test
    void updatePermission_ownerCanUpdate() {
        UpdateVoiceSharePermissionRequest request = new UpdateVoiceSharePermissionRequest();
        request.setPermission(SharePermission.WRITE);

        VoiceShare existing = new VoiceShare();
        existing.setId(10L);
        existing.setVoiceNoteId(VOICE_NOTE_ID);
        existing.setOwnerId(OWNER_ID);
        existing.setSharedWithUserId(RECIPIENT_ID);
        existing.setPermission(SharePermission.READ);

        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));
        when(voiceShareRepository.saveAndFlush(existing)).thenReturn(existing);
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(voiceNote);

        VoiceShareResponse expectedResponse = new VoiceShareResponse(
                10L, VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID, SharePermission.WRITE, LocalDateTime.now(), LocalDateTime.now());
        when(voiceShareMapper.toResponse(existing)).thenReturn(expectedResponse);

        VoiceShareResponse result = service.updatePermission(OWNER_ID, VOICE_NOTE_ID, RECIPIENT_ID, request);

        assertThat(result).isEqualTo(expectedResponse);
        assertThat(existing.getPermission()).isEqualTo(SharePermission.WRITE);

        ArgumentCaptor<VoiceSharePermissionChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(VoiceSharePermissionChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().previousPermission()).isEqualTo(SharePermission.READ);
        assertThat(eventCaptor.getValue().newPermission()).isEqualTo(SharePermission.WRITE);
    }

    @Test
    void updatePermission_sameLevelIsIdempotentNoOp() {
        UpdateVoiceSharePermissionRequest request = new UpdateVoiceSharePermissionRequest();
        request.setPermission(SharePermission.READ);

        VoiceShare existing = new VoiceShare();
        existing.setId(10L);
        existing.setVoiceNoteId(VOICE_NOTE_ID);
        existing.setOwnerId(OWNER_ID);
        existing.setSharedWithUserId(RECIPIENT_ID);
        existing.setPermission(SharePermission.READ);

        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));

        VoiceShareResponse expectedResponse = new VoiceShareResponse(
                10L, VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID, SharePermission.READ, LocalDateTime.now(), null);
        when(voiceShareMapper.toResponse(existing)).thenReturn(expectedResponse);

        VoiceShareResponse result = service.updatePermission(OWNER_ID, VOICE_NOTE_ID, RECIPIENT_ID, request);

        assertThat(result).isEqualTo(expectedResponse);
        verify(voiceShareRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updatePermission_grantNotFound_throws404Equivalent() {
        UpdateVoiceSharePermissionRequest request = new UpdateVoiceSharePermissionRequest();
        request.setPermission(SharePermission.WRITE);

        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePermission(OWNER_ID, VOICE_NOTE_ID, RECIPIENT_ID, request))
                .isInstanceOf(VoiceShareNotFoundException.class);
    }

    @Test
    void revoke_ownerCanRevoke() {
        VoiceShare existing = new VoiceShare();
        existing.setId(10L);
        existing.setVoiceNoteId(VOICE_NOTE_ID);
        existing.setOwnerId(OWNER_ID);
        existing.setSharedWithUserId(RECIPIENT_ID);
        existing.setPermission(SharePermission.READ);

        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(existing));
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(voiceNote);

        service.revoke(OWNER_ID, VOICE_NOTE_ID, RECIPIENT_ID);

        verify(voiceShareRepository, times(1)).delete(existing);

        ArgumentCaptor<VoiceShareRevokedEvent> eventCaptor = ArgumentCaptor.forClass(VoiceShareRevokedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().voiceNoteId()).isEqualTo(VOICE_NOTE_ID);
        assertThat(eventCaptor.getValue().sharedWithUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(eventCaptor.getValue().voiceNoteTitle()).isEqualTo("memo.m4a");
    }

    @Test
    void revoke_grantNotFound_throws404Equivalent() {
        when(voiceShareRepository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(VOICE_NOTE_ID, OWNER_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(OWNER_ID, VOICE_NOTE_ID, RECIPIENT_ID))
                .isInstanceOf(VoiceShareNotFoundException.class);

        verify(voiceShareRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

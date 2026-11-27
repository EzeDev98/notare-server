package com.notare.feature.sync.service.impl;

import com.notare.feature.note.dto.response.NoteChange;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.sync.config.SyncProperties;
import com.notare.feature.sync.dto.response.SyncPullResponse;
import com.notare.feature.sync.model.NoteTombstone;
import com.notare.feature.sync.repository.NoteTombstoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for SyncPullServiceImpl. */
@ExtendWith(MockitoExtension.class)
class SyncPullServiceImplTest {

    private static final Long OWNER_ID = 1L;

    @Mock
    private NoteService noteService;

    @Mock
    private NoteTombstoneRepository noteTombstoneRepository;

    @Mock
    private com.notare.feature.voicenote.service.VoiceNoteService voiceNoteService;

    @Mock
    private com.notare.feature.transcription.service.TranscriptionService transcriptionService;

    @Captor
    private ArgumentCaptor<LocalDateTime> sinceCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private SyncProperties syncProperties;
    private SyncPullServiceImpl service;

    @BeforeEach
    void setUp() {
        syncProperties = new SyncProperties();
        syncProperties.setOverlapSeconds(5);
        syncProperties.setDefaultPageSize(100);
        syncProperties.setMaxPageSize(500);
        syncProperties.setRetentionDays(30);
        org.mockito.Mockito.lenient()
                .when(voiceNoteService.findClipChangesSince(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        org.mockito.Mockito.lenient()
                .when(transcriptionService.getForVoiceNotes(any()))
                .thenReturn(java.util.Map.of());
        service = new SyncPullServiceImpl(noteService, noteTombstoneRepository, syncProperties, voiceNoteService, transcriptionService);
    }

    private NoteResponse note(Long id) {
        return new NoteResponse(id, OWNER_ID, "hello", false, 1L, OWNER_ID, null, null);
    }

    private NoteChange changed(Long noteId, String clientId) {
        return new NoteChange(noteId, false, note(noteId), clientId);
    }

    private NoteChange tombstoneChange(Long noteId) {
        return new NoteChange(noteId, true, null, null);
    }

    @Test
    void pull_firstSync_queriesWithANullSinceAndSkipsTheOverlapRewind() {
        when(noteService.findChangesSince(eq(OWNER_ID), sinceCaptor.capture(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.pull(OWNER_ID, null, Pageable.unpaged());

        assertThat(sinceCaptor.getValue()).isNull();
    }

    @Test
    void pull_firstSync_neverQueriesTombstones() {
        when(noteService.findChangesSince(eq(OWNER_ID), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(changed(1L, null))));

        service.pull(OWNER_ID, null, Pageable.unpaged());

        verifyNoInteractions(noteTombstoneRepository);
    }

    @Test
    void pull_incrementalSync_rewindsTheCursorByTheConfiguredOverlap() {
        LocalDateTime cursor = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        when(noteService.findChangesSince(eq(OWNER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(noteTombstoneRepository.findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any()))
                .thenReturn(List.of());

        service.pull(OWNER_ID, cursor, Pageable.unpaged());

        verify(noteService).findChangesSince(eq(OWNER_ID), eq(cursor.minusSeconds(5)), any());
    }

    @Test
    void pull_incrementalSync_queriesTombstonesUsingTheSameRewoundWindow() {
        LocalDateTime cursor = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        when(noteService.findChangesSince(eq(OWNER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(noteTombstoneRepository.findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any()))
                .thenReturn(List.of());

        service.pull(OWNER_ID, cursor, Pageable.unpaged());

        verify(noteTombstoneRepository)
                .findByOwnerIdAndDeletedAtGreaterThanEqual(OWNER_ID, cursor.minusSeconds(5));
    }

    @Test
    void pull_splitsActiveChangesAndTombstonedRowsFromTheNoteModuleCorrectly() {
        NoteChange active = changed(1L, "local-1");
        NoteChange trashed = tombstoneChange(2L);
        when(noteService.findChangesSince(eq(OWNER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of(active, trashed)));
        when(noteTombstoneRepository.findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any()))
                .thenReturn(List.of());

        SyncPullResponse response = service.pull(OWNER_ID, LocalDateTime.now(), Pageable.unpaged());

        assertThat(response.changed()).hasSize(1);
        assertThat(response.changed().get(0).note().id()).isEqualTo(1L);
        assertThat(response.changed().get(0).clientId()).isEqualTo("local-1");
        assertThat(response.deletedIds()).containsExactly(2L);
    }

    @Test
    void pull_appendsPermanentDeleteTombstonesToTheDeletedIdsListAlongsideTrashedNotes() {
        NoteChange trashed = tombstoneChange(2L);
        when(noteService.findChangesSince(eq(OWNER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of(trashed)));
        NoteTombstone permanentlyDeleted = new NoteTombstone();
        permanentlyDeleted.setNoteId(3L);
        permanentlyDeleted.setOwnerId(OWNER_ID);
        permanentlyDeleted.setDeletedAt(LocalDateTime.now());
        when(noteTombstoneRepository.findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any()))
                .thenReturn(List.of(permanentlyDeleted));

        SyncPullResponse response = service.pull(OWNER_ID, LocalDateTime.now(), Pageable.unpaged());

        assertThat(response.deletedIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void pull_hasMoreTrue_echoesBackTheClientsOwnCursorUnchanged() {
        LocalDateTime cursor = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        Page<NoteChange> pageWithMore = new PageImpl<>(
                List.of(changed(1L, null)), PageRequest.of(0, 1), 5);
        when(noteService.findChangesSince(eq(OWNER_ID), any(), any())).thenReturn(pageWithMore);
        when(noteTombstoneRepository.findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any()))
                .thenReturn(List.of());

        SyncPullResponse response = service.pull(OWNER_ID, cursor, PageRequest.of(0, 1));

        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(cursor);
    }

    @Test
    void pull_hasMoreFalse_advancesTheCursorToTheSnapshotTakenBeforeTheQuery() {
        LocalDateTime cursor = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        when(noteService.findChangesSince(eq(OWNER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of(changed(1L, null))));
        when(noteTombstoneRepository.findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any()))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        SyncPullResponse response = service.pull(OWNER_ID, cursor, Pageable.unpaged());
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isBetween(before, after);
    }

    @Test
    void pull_oversizedPageRequest_isClampedToTheConfiguredMaximum() {
        when(noteService.findChangesSince(eq(OWNER_ID), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.pull(OWNER_ID, LocalDateTime.now(), PageRequest.of(2, 10_000));

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(500);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
    }

    @Test
    void pull_unpagedRequest_fallsBackToTheDefaultPageSizeAtPageZero() {
        when(noteService.findChangesSince(eq(OWNER_ID), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.pull(OWNER_ID, LocalDateTime.now(), Pageable.unpaged());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void pull_requestedPageSizeUnderTheCeiling_isLeftAsRequested() {
        when(noteService.findChangesSince(eq(OWNER_ID), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.pull(OWNER_ID, LocalDateTime.now(), PageRequest.of(0, 50));

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void pull_tombstoneRepositoryIsNeverConsultedWhenNoTombstonesFallInTheWindow() {
        when(noteService.findChangesSince(eq(OWNER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(noteTombstoneRepository.findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any()))
                .thenReturn(List.of());

        SyncPullResponse response = service.pull(OWNER_ID, LocalDateTime.now(), Pageable.unpaged());

        assertThat(response.deletedIds()).isEmpty();
        verify(noteTombstoneRepository).findByOwnerIdAndDeletedAtGreaterThanEqual(eq(OWNER_ID), any());
    }
}

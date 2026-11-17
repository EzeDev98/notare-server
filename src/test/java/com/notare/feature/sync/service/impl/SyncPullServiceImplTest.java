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

/**
 * Step 13b of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link SyncPullServiceImpl}'s source and follows this codebase's existing
 * Mockito conventions exactly (see {@code VoiceUploadValidatorTest} for the
 * pattern of instantiating a real {@code @ConfigurationProperties} bean
 * directly rather than mocking it), but it has not actually been compiled or
 * run. Please run it via {@code mvn test} before trusting it.
 *
 * <p>{@code SyncProperties} is a plain settable bean, so these tests
 * construct a real instance with fixed values rather than mocking getters —
 * consistent with how this codebase treats every other properties class.
 *
 * <p>Covers, per the class's own extensive inline reasoning:
 * <ul>
 *   <li>the overlap window: an incremental pull queries
 *       {@code since.minusSeconds(overlapSeconds)}, never the raw cursor;</li>
 *   <li>a first sync ({@code since == null}) skips the overlap rewind AND
 *       skips the tombstone query entirely — a client with nothing cached
 *       has nothing to evict;</li>
 *   <li>{@link NoteChange} rows split correctly into the changed list and
 *       the deleted-ids list based on {@code deleted()};</li>
 *   <li>tombstones are appended to {@code deletedIds} only on an incremental
 *       sync, using the SAME rewound window as the note query;</li>
 *   <li>the cursor-holding rule: while {@code hasMore} is true the response
 *       echoes back the client's OWN {@code since} unchanged (not the
 *       snapshot, not the last row's timestamp) so paging within one window
 *       doesn't skip or duplicate rows; once the window is drained the
 *       response advances to the snapshot taken before the query ran;</li>
 *   <li>page-size clamping: an oversized request is capped at
 *       {@code maxPageSize}, and an unpaged request falls back to
 *       {@code defaultPageSize} at page zero.</li>
 * </ul>
 */
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

    // -------------------------------------------------------------------
    // First sync (no cursor)
    // -------------------------------------------------------------------

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

    // -------------------------------------------------------------------
    // Incremental sync — overlap window
    // -------------------------------------------------------------------

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

    // -------------------------------------------------------------------
    // Splitting changed vs. deleted
    // -------------------------------------------------------------------

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

    // -------------------------------------------------------------------
    // Cursor-holding rule
    // -------------------------------------------------------------------

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

    // -------------------------------------------------------------------
    // Page-size clamping
    // -------------------------------------------------------------------

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
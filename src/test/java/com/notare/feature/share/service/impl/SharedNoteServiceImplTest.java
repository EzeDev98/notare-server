package com.notare.feature.share.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientNotePermissionException;
import com.notare.common.exception.NoteShareNotFoundException;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.mapper.NoteShareMapper;
import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.service.VoiceShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 8c of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito and Spring Data are
 * unavailable here (see {@link
 * UserServiceImplementationTest} for the
 * full explanation). {@link NoteShareMapper} is real - no third-party
 * dependency of its own. Reasoned carefully from source; run via
 * {@code mvn test} before trusting it.
 *
 * <p>Covers {@link SharedNoteServiceImpl}, the recipient-side counterpart to
 * {@link NoteShareServiceImpl}. Its single authorization gate,
 * {@code requireGrant}, distinguishes two failures precisely (see the
 * class's own doc comment):
 *
 * <ul>
 *   <li><b>No grant at all -> {@link NoteShareNotFoundException} (404).</b>
 *       As far as this caller is concerned the note doesn't exist.</li>
 *   <li><b>Grant too weak -> {@link InsufficientNotePermissionException}
 *       (403).</b> A READ recipient attempting a write already knows the
 *       note exists — hiding it here would be incoherent.</li>
 * </ul>
 *
 * <p>Also covers the N+1 avoidance in {@code listSharedWithMe} (one batch
 * summary lookup for the whole page, joined in memory, with a stale grant on
 * a since-trashed note silently dropped rather than rendered as a hole), and
 * the belt-and-suspenders check in {@code getAttachedVoiceNoteAudio} that a
 * voice note's OWN {@code noteId} matches the note the caller has a grant on
 * — without it, a grant on note A plus a guessed voice-note id from note B
 * would leak note B's audio.
 */
@ExtendWith(MockitoExtension.class)
class SharedNoteServiceImplTest {

    @Mock private NoteShareRepository noteShareRepository;
    @Mock private com.notare.feature.note.service.NoteService noteService;
    @Mock private VoiceNoteService voiceNoteService;
    @Mock private VoiceShareService voiceShareService;

    private final NoteShareMapper noteShareMapper = new NoteShareMapper();

    private SharedNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SharedNoteServiceImpl(noteShareRepository, noteShareMapper, noteService, voiceNoteService, voiceShareService);
    }

    private NoteShare share(Long id, Long noteId, Long ownerId, Long sharedWithUserId, SharePermission permission) {
        NoteShare s = new NoteShare();
        s.setId(id);
        s.setNoteId(noteId);
        s.setOwnerId(ownerId);
        s.setSharedWithUserId(sharedWithUserId);
        s.setPermission(permission);
        s.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return s;
    }

    private NoteSummaryResponse summary(Long id, String preview) {
        return new NoteSummaryResponse(id, preview, false, false, 1L, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private NoteResponse noteResponse(Long id, String content) {
        return new NoteResponse(id, 1L, content, false, false, 1L, null,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private VoiceNoteResponse voiceNote(Long id, Long noteId) {
        return new VoiceNoteResponse(id, 9L, noteId, "clip.mp3", "audio/mpeg", 1024L, 30,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    // ---- listSharedWithMe -------------------------------------------------

    @Test
    void listSharedWithMe_resolvesAllNoteSummariesInOneBatchQuery_andJoinsInMemory() {
        NoteShare s1 = share(1L, 10L, 1L, 2L, SharePermission.READ);
        NoteShare s2 = share(2L, 20L, 1L, 2L, SharePermission.WRITE);
        Page<NoteShare> page = new PageImpl<>(List.of(s1, s2));
        when(noteShareRepository.findBySharedWithUserId(eq(2L), any())).thenReturn(page);
        when(noteService.getAuthorizedNoteSummaries(List.of(10L, 20L)))
                .thenReturn(Map.of(10L, summary(10L, "first"), 20L, summary(20L, "second")));

        PagedResponse<SharedNoteSummaryResponse> result = service.listSharedWithMe(2L, Pageable.unpaged());

        assertThat(result.content()).hasSize(2);
        // One batch call, not one per row.
        verify(noteService).getAuthorizedNoteSummaries(any());
    }

    @Test
    void listSharedWithMe_dropsARow_whoseNoteSummaryIsAbsent_ratherThanRenderingAHole() {
        NoteShare stale = share(1L, 10L, 1L, 2L, SharePermission.READ);
        Page<NoteShare> page = new PageImpl<>(List.of(stale));
        when(noteShareRepository.findBySharedWithUserId(eq(2L), any())).thenReturn(page);
        // The note behind this grant was trashed/deleted, so the batch lookup
        // simply omits it from the map.
        when(noteService.getAuthorizedNoteSummaries(List.of(10L))).thenReturn(Map.of());

        PagedResponse<SharedNoteSummaryResponse> result = service.listSharedWithMe(2L, Pageable.unpaged());

        assertThat(result.content()).isEmpty();
    }

    @Test
    void listSharedWithMe_totalElementsCanExceedTheReturnedContent_aKnownAcceptedInconsistency() {
        // Documented directly in the class: totalElements counts grants,
        // including ones whose note has since been trashed, so a page can
        // legitimately return fewer rows than the count implies.
        //
        // PageImpl(content, pageable, total) does its own sanity check: if
        // content isn't empty and offset + pageSize > total, it decides the
        // given total was wrong and silently recomputes it as offset +
        // content.size() instead. Pageable.ofSize(20) against a total of 5
        // trips that (0 + 20 > 5), which quietly collapsed the intended
        // total back down to 1 - the exact inconsistency this test means to
        // simulate. Pageable.ofSize(5) keeps offset + pageSize == total, so
        // PageImpl trusts the 5 we pass in instead of "fixing" it.
        NoteShare stale = share(1L, 10L, 1L, 2L, SharePermission.READ);
        Page<NoteShare> page = new PageImpl<>(List.of(stale), Pageable.ofSize(5), 5);
        when(noteShareRepository.findBySharedWithUserId(eq(2L), any())).thenReturn(page);
        when(noteService.getAuthorizedNoteSummaries(List.of(10L))).thenReturn(Map.of());

        PagedResponse<SharedNoteSummaryResponse> result = service.listSharedWithMe(2L, Pageable.unpaged());

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(5L);
    }

    // ---- getSharedNote -------------------------------------------------

    @Test
    void getSharedNote_throwsNoteShareNotFoundException_whenNoGrantExistsAtAll() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSharedNote(2L, 10L))
                .isInstanceOf(NoteShareNotFoundException.class)
                .hasMessage("Note not found.");
        verify(noteService, never()).getAuthorizedNote(anyLong());
    }

    @Test
    void getSharedNote_returnsTheAuthorizedNote_whenAtLeastAReadGrantExists() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.READ)));
        when(noteService.getAuthorizedNote(10L)).thenReturn(noteResponse(10L, "content"));

        NoteResponse response = service.getSharedNote(2L, 10L);

        assertThat(response.id()).isEqualTo(10L);
    }

    // ---- updateSharedNote -------------------------------------------------

    @Test
    void updateSharedNote_throwsInsufficientNotePermissionException_whenOnlyReadIsGranted() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.READ)));

        UpdateNoteRequest request = new UpdateNoteRequest();
        request.setContent("new");

        assertThatThrownBy(() -> service.updateSharedNote(2L, 10L, request))
                .isInstanceOf(InsufficientNotePermissionException.class)
                .hasMessage("You have read-only access to this note.");
        verify(noteService, never()).updateAuthorizedNote(anyLong(), any());
    }

    @Test
    void updateSharedNote_routesToUpdateAuthorizedNote_whenWriteIsGranted() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.WRITE)));
        UpdateNoteRequest request = new UpdateNoteRequest();
        request.setContent("new");
        when(noteService.updateAuthorizedNote(10L, request)).thenReturn(noteResponse(10L, "new"));

        NoteResponse response = service.updateSharedNote(2L, 10L, request);

        assertThat(response.content()).isEqualTo("new");
        verify(noteService).updateAuthorizedNote(10L, request);
    }

    // ---- autoSaveSharedNote -------------------------------------------------

    @Test
    void autoSaveSharedNote_throwsInsufficientNotePermissionException_whenOnlyReadIsGranted() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.READ)));

        AutoSaveNoteRequest request = new AutoSaveNoteRequest();

        assertThatThrownBy(() -> service.autoSaveSharedNote(2L, 10L, request))
                .isInstanceOf(InsufficientNotePermissionException.class);
        verify(noteService, never()).autoSaveAuthorizedNote(anyLong(), any());
    }

    @Test
    void autoSaveSharedNote_routesToAutoSaveAuthorizedNote_whenWriteIsGranted() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.WRITE)));
        AutoSaveNoteRequest request = new AutoSaveNoteRequest();
        when(noteService.autoSaveAuthorizedNote(10L, request)).thenReturn(noteResponse(10L, "patched"));

        service.autoSaveSharedNote(2L, 10L, request);

        verify(noteService).autoSaveAuthorizedNote(10L, request);
    }

    // ---- listAttachedVoiceNotes -------------------------------------------------

    @Test
    void listAttachedVoiceNotes_requiresAtLeastAReadGrant_thenDelegatesToVoiceNoteService() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.READ)));
        when(voiceNoteService.getAuthorizedVoiceNotesByNote(10L)).thenReturn(List.of(voiceNote(5L, 10L)));

        List<VoiceNoteResponse> result = service.listAttachedVoiceNotes(2L, 10L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listAttachedVoiceNotes_throwsNoteShareNotFoundException_withNoGrantAtAll() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAttachedVoiceNotes(2L, 10L)).isInstanceOf(NoteShareNotFoundException.class);
    }

    // ---- uploadVoiceNote -------------------------------------------------

    @Test
    void uploadVoiceNote_requiresWriteGrant_beforeUploading() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.READ)));

        assertThatThrownBy(() -> service.uploadVoiceNote(2L, 10L, null, 30))
                .isInstanceOf(InsufficientNotePermissionException.class);
        verify(voiceNoteService, never()).upload(any(), any(), any(), any());
    }

    @Test
    void uploadVoiceNote_uploadsThenGrantsACourtesyReadShareBackToTheNotesOwner() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.WRITE)));
        VoiceNoteResponse uploaded = voiceNote(5L, 10L);
        when(voiceNoteService.upload(2L, null, 10L, 30)).thenReturn(uploaded);

        VoiceNoteResponse result = service.uploadVoiceNote(2L, 10L, null, 30);

        assertThat(result).isEqualTo(uploaded);
        ArgumentCaptor<ShareVoiceNoteRequest> captor = ArgumentCaptor.forClass(ShareVoiceNoteRequest.class);
        verify(voiceShareService).shareVoiceNote(eq(2L), eq(5L), captor.capture());
        assertThat(captor.getValue().getSharedWithUserId()).isEqualTo(1L); // the note's OWNER, not the uploader
        assertThat(captor.getValue().getPermission()).isEqualTo(SharePermission.READ);
    }

    @Test
    void uploadVoiceNote_stillReturnsTheUploadedVoiceNote_evenWhenTheCourtesyGrantFails() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.WRITE)));
        VoiceNoteResponse uploaded = voiceNote(5L, 10L);
        when(voiceNoteService.upload(2L, null, 10L, 30)).thenReturn(uploaded);
        when(voiceShareService.shareVoiceNote(any(), any(), any()))
                .thenThrow(new RuntimeException("grant creation hiccup"));

        VoiceNoteResponse actual = service.uploadVoiceNote(2L, 10L, null, 30);

        assertThat(actual).isEqualTo(uploaded);
    }

    // ---- getAttachedVoiceNoteAudio -------------------------------------------------

    @Test
    void getAttachedVoiceNoteAudio_throwsNoteShareNotFoundException_whenTheVoiceNoteBelongsToADifferentNote() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.READ)));
        // The voice note's OWN record says it belongs to note 999, not the note
        // the caller has a grant on (10).
        when(voiceNoteService.getAuthorizedVoiceNote(5L)).thenReturn(voiceNote(5L, 999L));

        assertThatThrownBy(() -> service.getAttachedVoiceNoteAudio(2L, 10L, 5L))
                .isInstanceOf(NoteShareNotFoundException.class)
                .hasMessage("Note not found.");
        verify(voiceNoteService, never()).getAuthorizedAudio(anyLong());
    }

    @Test
    void getAttachedVoiceNoteAudio_returnsTheAudio_whenTheVoiceNoteBelongsToTheGrantedNote() {
        when(noteShareRepository.findByNoteIdAndSharedWithUserId(10L, 2L))
                .thenReturn(Optional.of(share(1L, 10L, 1L, 2L, SharePermission.READ)));
        when(voiceNoteService.getAuthorizedVoiceNote(5L)).thenReturn(voiceNote(5L, 10L));
        VoiceNoteContent content = new VoiceNoteContent(null, "audio/mpeg", "clip.mp3", 1024L);
        when(voiceNoteService.getAuthorizedAudio(5L)).thenReturn(content);

        VoiceNoteContent result = service.getAttachedVoiceNoteAudio(2L, 10L, 5L);

        assertThat(result).isEqualTo(content);
    }
}
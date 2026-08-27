package com.notare.feature.share.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidNoteShareException;
import com.notare.common.exception.NoteAlreadySharedException;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.common.exception.NoteShareNotFoundException;
import com.notare.common.util.NoteLabeler;
import com.notare.feature.collaboration.service.CollaborationService;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.share.dto.request.ShareNoteRequest;
import com.notare.feature.share.dto.request.UpdateSharePermissionRequest;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.event.NoteSharePermissionChangedEvent;
import com.notare.feature.share.event.NoteShareRevokedEvent;
import com.notare.feature.share.event.NoteSharedEvent;
import com.notare.feature.share.mapper.NoteShareMapper;
import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 8b of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito and Spring Data are
 * unavailable here (see {@link
 * com.notare.feature.user.service.impl.UserServiceImplementationTest} for the
 * full explanation). {@link NoteShareMapper} and {@link NoteLabeler} are both
 * real (no third-party dependency, and {@code NoteLabeler} was executed and
 * confirmed in Step 1). Reasoned carefully from source; run via
 * {@code mvn test} before trusting it.
 *
 * <p>Covers {@link NoteShareServiceImpl}'s ordered authorization gates and its
 * event-carries-a-snapshot-label discipline:
 *
 * <ul>
 *   <li>{@code shareNote}'s four gates run in a DELIBERATE order (self-share,
 *       then ownership, then accepted-collaborator, then already-shared) -
 *       the class's own doc comment explains that ownership must be checked
 *       BEFORE the collaborator check, or probing someone else's note id
 *       would answer 400 for a non-collaborator and 404 for a real one,
 *       leaking which note ids exist.</li>
 *   <li>Every event that carries a {@code noteLabel} gets it via {@link
 *       NoteLabeler#deriveLabel}, read from the note BEFORE the mutating
 *       step - most visibly in {@code revoke}, which reads the note's
 *       content before deleting the grant, since after deletion the
 *       recipient can no longer fetch it at all.</li>
 *   <li>{@code updatePermission} is idempotent when the requested level
 *       already matches: no write, no event - re-POSTing the same
 *       permission must not bump {@code updatedAt} or produce a
 *       notification about nothing changing.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NoteShareServiceImplTest {

    @Mock private NoteShareRepository noteShareRepository;
    @Mock private NoteService noteService;
    @Mock private CollaborationService collaborationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final NoteShareMapper noteShareMapper = new NoteShareMapper();

    private NoteShareServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoteShareServiceImpl(noteShareRepository, noteShareMapper, noteService, collaborationService, eventPublisher);
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

    private NoteResponse note(Long id, String content) {
        return new NoteResponse(id, 1L, content, false, false, 1L, null,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private ShareNoteRequest shareNoteRequest(Long recipientId, SharePermission permission) {
        ShareNoteRequest request = new ShareNoteRequest();
        request.setSharedWithUserId(recipientId);
        request.setPermission(permission);
        return request;
    }

    // ---- shareNote -------------------------------------------------

    @Test
    void shareNote_throwsInvalidNoteShareException_whenSharingWithSelf_beforeAnyOtherCheck() {
        assertThatThrownBy(() -> service.shareNote(1L, 5L, shareNoteRequest(1L, SharePermission.READ)))
                .isInstanceOf(InvalidNoteShareException.class)
                .hasMessage("You cannot share a note with yourself.");
        verifyNoInteractions(noteService, collaborationService, noteShareRepository, eventPublisher);
    }

    @Test
    void shareNote_propagatesNoteNotFoundException_whenTheNoteIsNotOwnedByTheCaller_beforeCheckingCollaboration() {
        // Ownership runs BEFORE the collaborator check - the class's own doc
        // comment explains this ordering prevents a 400-vs-404 status oracle.
        NoteNotFoundException notFound = new NoteNotFoundException("Note not found.");
        when(noteService.getNote(1L, 5L)).thenThrow(notFound);

        assertThatThrownBy(() -> service.shareNote(1L, 5L, shareNoteRequest(2L, SharePermission.READ)))
                .isSameAs(notFound);
        verifyNoInteractions(collaborationService, noteShareRepository);
    }

    @Test
    void shareNote_throwsInvalidNoteShareException_whenRecipientIsNotAnAcceptedCollaborator() {
        when(noteService.getNote(1L, 5L)).thenReturn(note(5L, "content"));
        when(collaborationService.isAcceptedCollaborator(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> service.shareNote(1L, 5L, shareNoteRequest(2L, SharePermission.READ)))
                .isInstanceOf(InvalidNoteShareException.class)
                .hasMessage("You can only share notes with people who have accepted your collaboration invitation.");
        verifyNoInteractions(noteShareRepository);
    }

    @Test
    void shareNote_throwsNoteAlreadySharedException_whenAGrantAlreadyExists() {
        when(noteService.getNote(1L, 5L)).thenReturn(note(5L, "content"));
        when(collaborationService.isAcceptedCollaborator(1L, 2L)).thenReturn(true);
        when(noteShareRepository.existsByNoteIdAndSharedWithUserId(5L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.shareNote(1L, 5L, shareNoteRequest(2L, SharePermission.READ)))
                .isInstanceOf(NoteAlreadySharedException.class)
                .hasMessage("This note is already shared with that user. Update the permission instead.");
        verify(noteShareRepository, never()).save(any());
    }

    @Test
    void shareNote_createsTheGrant_andPublishesNoteSharedEventWithTheDerivedLabel() {
        when(noteService.getNote(1L, 5L)).thenReturn(note(5L, "Meeting notes\nmore content"));
        when(collaborationService.isAcceptedCollaborator(1L, 2L)).thenReturn(true);
        when(noteShareRepository.existsByNoteIdAndSharedWithUserId(5L, 2L)).thenReturn(false);
        when(noteShareRepository.save(any(NoteShare.class))).thenAnswer(inv -> {
            NoteShare s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        NoteShareResponse response = service.shareNote(1L, 5L, shareNoteRequest(2L, SharePermission.WRITE));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.permission()).isEqualTo(SharePermission.WRITE);

        ArgumentCaptor<NoteSharedEvent> captor = ArgumentCaptor.forClass(NoteSharedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new NoteSharedEvent(
                100L, 5L, NoteLabeler.deriveLabel("Meeting notes\nmore content"), 1L, 2L, SharePermission.WRITE));
    }

    @Test
    void shareNote_wrapsAConcurrentDataIntegrityViolation_intoNoteAlreadySharedException() {
        when(noteService.getNote(1L, 5L)).thenReturn(note(5L, "content"));
        when(collaborationService.isAcceptedCollaborator(1L, 2L)).thenReturn(true);
        when(noteShareRepository.existsByNoteIdAndSharedWithUserId(5L, 2L)).thenReturn(false);
        when(noteShareRepository.save(any(NoteShare.class))).thenThrow(new DataIntegrityViolationException("race"));

        assertThatThrownBy(() -> service.shareNote(1L, 5L, shareNoteRequest(2L, SharePermission.READ)))
                .isInstanceOf(NoteAlreadySharedException.class);
        verifyNoInteractions(eventPublisher);
    }

    // ---- updatePermission -------------------------------------------------

    @Test
    void updatePermission_throwsNoteShareNotFoundException_whenNoOwnedGrantMatches() {
        when(noteShareRepository.findByNoteIdAndOwnerIdAndSharedWithUserId(5L, 1L, 2L)).thenReturn(Optional.empty());

        UpdateSharePermissionRequest request = new UpdateSharePermissionRequest();
        request.setPermission(SharePermission.WRITE);

        assertThatThrownBy(() -> service.updatePermission(1L, 5L, 2L, request))
                .isInstanceOf(NoteShareNotFoundException.class)
                .hasMessage("This note is not shared with that user.");
    }

    @Test
    void updatePermission_isIdempotentAndPublishesNoEvent_whenTheRequestedLevelAlreadyMatches() {
        NoteShare existing = share(100L, 5L, 1L, 2L, SharePermission.WRITE);
        when(noteShareRepository.findByNoteIdAndOwnerIdAndSharedWithUserId(5L, 1L, 2L)).thenReturn(Optional.of(existing));

        UpdateSharePermissionRequest request = new UpdateSharePermissionRequest();
        request.setPermission(SharePermission.WRITE);

        service.updatePermission(1L, 5L, 2L, request);

        verify(noteShareRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher, noteService);
    }

    @Test
    void updatePermission_changesTheLevel_savesAndFlushes_andPublishesThePermissionChangedEvent() {
        NoteShare existing = share(100L, 5L, 1L, 2L, SharePermission.READ);
        when(noteShareRepository.findByNoteIdAndOwnerIdAndSharedWithUserId(5L, 1L, 2L)).thenReturn(Optional.of(existing));
        when(noteShareRepository.saveAndFlush(existing)).thenReturn(existing);
        when(noteService.getNote(1L, 5L)).thenReturn(note(5L, "Project plan details"));

        UpdateSharePermissionRequest request = new UpdateSharePermissionRequest();
        request.setPermission(SharePermission.WRITE);

        service.updatePermission(1L, 5L, 2L, request);

        assertThat(existing.getPermission()).isEqualTo(SharePermission.WRITE);
        ArgumentCaptor<NoteSharePermissionChangedEvent> captor =
                ArgumentCaptor.forClass(NoteSharePermissionChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().previousPermission()).isEqualTo(SharePermission.READ);
        assertThat(captor.getValue().newPermission()).isEqualTo(SharePermission.WRITE);
        assertThat(captor.getValue().noteLabel()).isEqualTo(NoteLabeler.deriveLabel("Project plan details"));
    }

    // ---- revoke -------------------------------------------------

    @Test
    void revoke_throwsNoteShareNotFoundException_whenNoOwnedGrantMatches() {
        when(noteShareRepository.findByNoteIdAndOwnerIdAndSharedWithUserId(5L, 1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(1L, 5L, 2L)).isInstanceOf(NoteShareNotFoundException.class);
    }

    @Test
    void revoke_readsTheNoteContentBeforeDeletingTheGrant_thenPublishesTheRevokedEvent() {
        NoteShare existing = share(100L, 5L, 1L, 2L, SharePermission.READ);
        when(noteShareRepository.findByNoteIdAndOwnerIdAndSharedWithUserId(5L, 1L, 2L)).thenReturn(Optional.of(existing));
        when(noteService.getNote(1L, 5L)).thenReturn(note(5L, "Grocery list"));

        service.revoke(1L, 5L, 2L);

        InOrder order = inOrder(noteService, noteShareRepository);
        order.verify(noteService).getNote(1L, 5L);
        order.verify(noteShareRepository).delete(existing);

        verify(eventPublisher).publishEvent(new NoteShareRevokedEvent(
                5L, NoteLabeler.deriveLabel("Grocery list"), 1L, 2L));
    }

    // ---- listShares -------------------------------------------------

    @Test
    void listShares_verifiesOwnershipExplicitly_evenThoughTheQueryIsAlsoOwnerScoped() {
        NoteNotFoundException notFound = new NoteNotFoundException("Note not found.");
        when(noteService.getNote(1L, 5L)).thenThrow(notFound);

        assertThatThrownBy(() -> service.listShares(1L, 5L, Pageable.unpaged())).isSameAs(notFound);
        verifyNoInteractions(noteShareRepository);
    }

    @Test
    void listShares_mapsThePageOfGrantsToResponses() {
        when(noteService.getNote(1L, 5L)).thenReturn(note(5L, "content"));
        NoteShare s = share(100L, 5L, 1L, 2L, SharePermission.READ);
        Page<NoteShare> page = new PageImpl<>(List.of(s));
        when(noteShareRepository.findByNoteIdAndOwnerId(eq(5L), eq(1L), any())).thenReturn(page);

        PagedResponse<NoteShareResponse> result = service.listShares(1L, 5L, Pageable.unpaged());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).sharedWithUserId()).isEqualTo(2L);
    }
}
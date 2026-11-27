package com.notare.feature.note.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.common.util.LikeEscaper;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteChange;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.note.mapper.NoteMapper;
import com.notare.feature.note.model.Note;
import com.notare.feature.note.repository.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for NoteServiceImpl. */
@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    @Mock private NoteRepository noteRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final NoteMapper noteMapper = new NoteMapper();

    private NoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoteServiceImpl(noteRepository, noteMapper, eventPublisher);
    }

    private Note note(Long id, Long ownerId, String content, Long version) {
        Note note = new Note();
        note.setId(id);
        note.setOwnerId(ownerId);
        note.setContent(content);
        note.setVersion(version);
        note.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        note.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        return note;
    }

    private CreateNoteRequest createNoteRequest(String content) {
        CreateNoteRequest request = new CreateNoteRequest();
        request.setContent(content);
        return request;
    }

    private UpdateNoteRequest updateNoteRequest(String content, Long version) {
        UpdateNoteRequest request = new UpdateNoteRequest();
        request.setContent(content);
        return request;
    }

    private AutoSaveNoteRequest autoSaveNoteRequest(String content, Long version) {
        AutoSaveNoteRequest request = new AutoSaveNoteRequest();
        request.setContent(content);
        return request;
    }

    @Test
    void createNote_mapsTheRequestAndSaves_thenReturnsTheMappedResponse() {
        CreateNoteRequest request = createNoteRequest("hello world");
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note saved = inv.getArgument(0);
            saved.setId(1L);
            saved.setVersion(0L);
            return saved;
        });

        NoteResponse response = service.createNote(9L, request);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(9L);
        assertThat(captor.getValue().getContent()).isEqualTo("hello world");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.ownerId()).isEqualTo(9L);
        assertThat(response.content()).isEqualTo("hello world");
        assertThat(response.version()).isEqualTo(0L);
    }

    @Test
    void getNote_returnsTheMappedNote_whenItIsActiveAndOwnedByTheCaller() {
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L))
                .thenReturn(Optional.of(note(5L, 9L, "content", 2L)));

        NoteResponse response = service.getNote(9L, 5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.content()).isEqualTo("content");
    }

    @Test
    void getNote_throwsNoteNotFoundException_whenNoActiveOwnedNoteMatches() {
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNote(9L, 5L))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessage("Note not found.");
    }

    @Test
    void listNotes_queriesFindByOwnerIdAndDeletedFalse() {
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listNotes(9L, Pageable.unpaged());

        verify(noteRepository).findByOwnerIdAndDeletedFalse(eq(9L), any());
    }

    @Test
    void listNotes_appendsAnIdDescTiebreaker_whenTheCallersSortHasNoIdOrder() {
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("updatedAt")));
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listNotes(9L, requested);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(noteRepository).findByOwnerIdAndDeletedFalse(eq(9L), captor.capture());
        Sort appliedSort = captor.getValue().getSort();
        assertThat(appliedSort.getOrderFor("updatedAt")).isNotNull();
        assertThat(appliedSort.getOrderFor("id")).isNotNull();
        assertThat(appliedSort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listNotes_leavesTheSortAlone_whenTheCallerAlreadyOrdersById() {
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("id")));
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listNotes(9L, requested);

        verify(noteRepository).findByOwnerIdAndDeletedFalse(9L, requested);
    }

    @Test
    void listNotes_leavesAnUnsortedPageableAlone_addingNoOrderByAtAll() {
        Pageable requested = PageRequest.of(0, 20);
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listNotes(9L, requested);

        verify(noteRepository).findByOwnerIdAndDeletedFalse(9L, requested);
    }

    @Test
    void listNotes_mapsPageContentToSummaries_andBuildsThePagedEnvelope() {
        Note note = note(1L, 9L, "some content", 3L);
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of(note)));

        PagedResponse<NoteSummaryResponse> result = service.listNotes(9L, Pageable.unpaged());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(1L);
        assertThat(result.content().get(0).contentPreview()).isEqualTo("some content");
    }

    @Test
    void setPinned_throwsNoteNotFoundException_whenNoActiveOwnedNoteMatches() {
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPinned(9L, 5L, true))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessage("Note not found.");
        verify(noteRepository, never()).saveAndFlush(any());
    }

    @Test
    void setPinned_flipsTheFlagAndSavesAndFlushesImmediately() {
        Note note = note(5L, 9L, "x", 1L);
        note.setPinned(false);
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.of(note));
        when(noteRepository.saveAndFlush(note)).thenReturn(note);

        NoteResponse response = service.setPinned(9L, 5L, true);

        assertThat(note.isPinned()).isTrue();
        verify(noteRepository).saveAndFlush(note);
        assertThat(response.pinned()).isTrue();
    }

    @Test
    void searchNotes_treatsANullQueryAsAnEmptyTerm_behavingLikeListAll() {
        when(noteRepository.searchByOwner(eq(9L), eq(""), any())).thenReturn(new PageImpl<>(List.of()));

        service.searchNotes(9L, null, Pageable.unpaged());

        verify(noteRepository).searchByOwner(eq(9L), eq(""), any());
    }

    @Test
    void searchNotes_stripsAndEscapesTheTermBeforeQuerying() {
        String rawTerm = " 50%_off ";
        String expected = LikeEscaper.escape(rawTerm.strip());
        when(noteRepository.searchByOwner(eq(9L), eq(expected), any())).thenReturn(new PageImpl<>(List.of()));

        service.searchNotes(9L, rawTerm, Pageable.unpaged());

        verify(noteRepository).searchByOwner(9L, expected, Pageable.unpaged());
    }

    @Test
    void searchNotes_doesNotApplyWithStableOrder_unlikeListNotesAndListTrashedNotes() {
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("updatedAt")));
        when(noteRepository.searchByOwner(eq(9L), eq(""), eq(requested))).thenReturn(new PageImpl<>(List.of()));

        service.searchNotes(9L, "", requested);

        verify(noteRepository).searchByOwner(9L, "", requested);
    }

    @Test
    void updateNote_throwsNoteNotFoundException_whenNoActiveOwnedNoteMatches() {
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateNote(9L, 5L, updateNoteRequest("new", 1L)))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessage("Note not found.");
    }

    @Test
    void updateNote_clearsTheContent_whenTheRequestsContentIsNull_thisIsPutSemantics() {
        Note note = note(5L, 9L, "old content", 3L);
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.of(note));
        when(noteRepository.saveAndFlush(note)).thenReturn(note);

        service.updateNote(9L, 5L, updateNoteRequest(null, 3L));

        assertThat(note.getContent()).isNull();
        verify(noteRepository).saveAndFlush(note);
    }

    @Test
    void updateNote_appliesTheNewContent_andSavesAndFlushesImmediately() {
        Note note = note(5L, 9L, "old", 3L);
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.of(note));
        when(noteRepository.saveAndFlush(note)).thenReturn(note);

        NoteResponse response = service.updateNote(9L, 5L, updateNoteRequest("brand new", 3L));

        assertThat(note.getContent()).isEqualTo("brand new");
        assertThat(response.content()).isEqualTo("brand new");
    }

    @Test
    void autoSave_throwsNoteNotFoundException_whenNoActiveOwnedNoteMatches() {
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.autoSave(9L, 5L, autoSaveNoteRequest("x", 1L)))
                .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void autoSave_leavesContentUnchanged_whenTheRequestsContentIsNull_thisIsPatchSemantics() {
        Note note = note(5L, 9L, "untouched", 3L);
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.of(note));
        when(noteRepository.saveAndFlush(note)).thenReturn(note);

        NoteResponse response = service.autoSave(9L, 5L, autoSaveNoteRequest(null, 3L));

        assertThat(note.getContent()).isEqualTo("untouched");
        assertThat(response.content()).isEqualTo("untouched");
    }

    @Test
    void autoSave_appliesTheNewContent_whenItIsNonNull() {
        Note note = note(5L, 9L, "old", 3L);
        when(noteRepository.findByIdAndOwnerIdAndDeletedFalse(5L, 9L)).thenReturn(Optional.of(note));
        when(noteRepository.saveAndFlush(note)).thenReturn(note);

        service.autoSave(9L, 5L, autoSaveNoteRequest("patched", 3L));

        assertThat(note.getContent()).isEqualTo("patched");
    }

    @Test
    void permanentlyDeleteNote_throwsNoteNotFoundException_whenNoOwnedNoteMatchesRegardlessOfDeletedState() {
        when(noteRepository.findByIdAndOwnerId(5L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.permanentlyDeleteNote(9L, 5L)).isInstanceOf(NoteNotFoundException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void permanentlyDeleteNote_deletesTheEntityDirectly_neverByBareId() {
        Note note = note(5L, 9L, "x", 1L);
        when(noteRepository.findByIdAndOwnerId(5L, 9L)).thenReturn(Optional.of(note));

        service.permanentlyDeleteNote(9L, 5L);

        verify(noteRepository).delete(note);
        verify(noteRepository, never()).deleteById(anyLong());
    }

    @Test
    void permanentlyDeleteNote_publishesNotePermanentlyDeletedEvent_withBothNoteIdAndOwnerId() {
        Note note = note(5L, 9L, "x", 1L);
        when(noteRepository.findByIdAndOwnerId(5L, 9L)).thenReturn(Optional.of(note));

        service.permanentlyDeleteNote(9L, 5L);

        ArgumentCaptor<NotePermanentlyDeletedEvent> captor = ArgumentCaptor.forClass(NotePermanentlyDeletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().noteId()).isEqualTo(5L);
        assertThat(captor.getValue().ownerId()).isEqualTo(9L);
    }

    @Test
    void createWithClientId_returnsTheExistingNote_whenAReplayedClientIdIsAlreadyKnown_withoutSavingAgain() {
        Note existing = note(1L, 9L, "already created", 0L);
        when(noteRepository.findByOwnerIdAndClientId(9L, "client-abc")).thenReturn(Optional.of(existing));

        NoteResponse response = service.createWithClientId(9L, createNoteRequest("ignored"), "client-abc");

        assertThat(response.id()).isEqualTo(1L);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void createWithClientId_createsANewNoteWithTheGivenClientId_whenNoneExistsYet() {
        when(noteRepository.findByOwnerIdAndClientId(9L, "client-abc")).thenReturn(Optional.empty());
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        service.createWithClientId(9L, createNoteRequest("hello"), "client-abc");

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo("client-abc");
        assertThat(captor.getValue().getContent()).isEqualTo("hello");
    }

    @Test
    void createWithClientId_onALostRaceWithAConcurrentReplay_returnsTheWinningAttemptsNote() {
        when(noteRepository.findByOwnerIdAndClientId(9L, "client-abc"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(note(3L, 9L, "the winner's note", 0L)));
        DataIntegrityViolationException raceLoss = new DataIntegrityViolationException("uq_notes_owner_client_id");
        when(noteRepository.save(any(Note.class))).thenThrow(raceLoss);

        NoteResponse response = service.createWithClientId(9L, createNoteRequest("mine"), "client-abc");

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.content()).isEqualTo("the winner's note");
        verify(noteRepository, times(2)).findByOwnerIdAndClientId(9L, "client-abc");
    }

    @Test
    void createWithClientId_rethrowsTheOriginalViolation_ifTheRaceLookupAlsoComesUpEmpty() {
        when(noteRepository.findByOwnerIdAndClientId(9L, "client-abc")).thenReturn(Optional.empty());
        DataIntegrityViolationException raceLoss = new DataIntegrityViolationException("some other constraint");
        when(noteRepository.save(any(Note.class))).thenThrow(raceLoss);

        assertThatThrownBy(() -> service.createWithClientId(9L, createNoteRequest("mine"), "client-abc"))
                .isSameAs(raceLoss);
    }

    @Test
    void findAnyNote_returnsTheNote_evenWhenItIsTrashed_becauseThereIsNoDeletedFilter() {
        Note trashed = note(5L, 9L, "x", 1L);
        trashed.setDeleted(true);
        when(noteRepository.findByIdAndOwnerId(5L, 9L)).thenReturn(Optional.of(trashed));

        Optional<NoteResponse> result = service.findAnyNote(9L, 5L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(5L);
    }

    @Test
    void findAnyNote_returnsEmpty_whenNoOwnedNoteMatchesAtAll() {
        when(noteRepository.findByIdAndOwnerId(5L, 9L)).thenReturn(Optional.empty());

        assertThat(service.findAnyNote(9L, 5L)).isEmpty();
    }

    @Test
    void findChangesSince_withNoCursor_queriesActiveNotesOnly() {
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any())).thenReturn(new PageImpl<>(List.of()));

        service.findChangesSince(9L, null, Pageable.unpaged());

        verify(noteRepository).findByOwnerIdAndDeletedFalse(eq(9L), any());
        verify(noteRepository, never()).findByOwnerIdAndUpdatedAtGreaterThanEqual(any(), any(), any());
    }

    @Test
    void findChangesSince_withACursor_queriesUpdatedAtGreaterThanEqual() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(noteRepository.findByOwnerIdAndUpdatedAtGreaterThanEqual(eq(9L), eq(since), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.findChangesSince(9L, since, Pageable.unpaged());

        verify(noteRepository).findByOwnerIdAndUpdatedAtGreaterThanEqual(eq(9L), eq(since), any());
        verify(noteRepository, never()).findByOwnerIdAndDeletedFalse(any(), any());
    }

    @Test
    void findChangesSince_alwaysRebuildsTheSortAsUpdatedAtThenId_discardingTheCallersSort() {
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("content")));
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any())).thenReturn(new PageImpl<>(List.of()));

        service.findChangesSince(9L, null, requested);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(noteRepository).findByOwnerIdAndDeletedFalse(eq(9L), captor.capture());
        Sort appliedSort = captor.getValue().getSort();

        assertThat(appliedSort.getOrderFor("content")).isNull();
        assertThat(appliedSort.getOrderFor("updatedAt")).isNotNull();
        assertThat(appliedSort.getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(appliedSort.getOrderFor("id")).isNotNull();
        assertThat(appliedSort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findChangesSince_mapsAnActiveNote_toAFullNoteChangeWithClientId() {
        Note note = note(1L, 9L, "content", 1L);
        note.setClientId("client-xyz");
        when(noteRepository.findByOwnerIdAndDeletedFalse(eq(9L), any())).thenReturn(new PageImpl<>(List.of(note)));

        Page<NoteChange> result = service.findChangesSince(9L, null, Pageable.unpaged());

        NoteChange change = result.getContent().get(0);
        assertThat(change.noteId()).isEqualTo(1L);
        assertThat(change.deleted()).isFalse();
        assertThat(change.note()).isNotNull();
        assertThat(change.clientId()).isEqualTo("client-xyz");
    }

    @Test
    void findChangesSince_mapsATrashedNote_toABareTombstoneWithNoContentAndNoClientId() {
        Note trashed = note(1L, 9L, "will not be shipped", 1L);
        trashed.setDeleted(true);
        trashed.setClientId("client-xyz");
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(noteRepository.findByOwnerIdAndUpdatedAtGreaterThanEqual(eq(9L), eq(since), any()))
                .thenReturn(new PageImpl<>(List.of(trashed)));

        Page<NoteChange> result = service.findChangesSince(9L, since, Pageable.unpaged());

        NoteChange change = result.getContent().get(0);
        assertThat(change.noteId()).isEqualTo(1L);
        assertThat(change.deleted()).isTrue();
        assertThat(change.note()).isNull();
        assertThat(change.clientId()).isNull();
    }

}

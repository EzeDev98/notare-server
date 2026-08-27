package com.notare.feature.voicenote.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidVoiceUploadException;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.common.exception.StorageException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.storage.FileStorageService;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.event.VoiceNoteCreatedEvent;
import com.notare.feature.voicenote.event.VoiceNoteDeletedEvent;
import com.notare.feature.voicenote.mapper.VoiceNoteMapper;
import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.repository.VoiceNoteRepository;
import com.notare.feature.voicenote.validation.VoiceUploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers {@link VoiceNoteServiceImpl#loadAudio}'s authorization, mirroring
 * the two paths its own doc comment describes: direct ownership (the common
 * case, and the only path for a standalone recording), and — new — falling
 * back to "does the caller own the recording's parent note", so a note
 * owner can finally play back a WRITE collaborator's voice reply the same
 * way {@link VoiceNoteServiceImpl#listVoiceNotesForNote} already lets them
 * see it listed.
 */
@ExtendWith(MockitoExtension.class)
class VoiceNoteServiceImplTest {

    private static final Long NOTE_OWNER_ID = 1L;
    private static final Long COLLABORATOR_ID = 2L;
    private static final Long STRANGER_ID = 99L;
    private static final Long VOICE_NOTE_ID = 100L;
    private static final Long NOTE_ID = 42L;

    @Mock
    private VoiceNoteRepository voiceNoteRepository;

    @Mock
    private VoiceNoteMapper voiceNoteMapper;

    @Mock
    private VoiceUploadValidator voiceUploadValidator;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NoteService noteService;

    private VoiceNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VoiceNoteServiceImpl(
                voiceNoteRepository, voiceNoteMapper, voiceUploadValidator,
                fileStorageService, eventPublisher, noteService);
    }

    private VoiceNote attachedRecording(Long uploaderId) {
        VoiceNote voiceNote = new VoiceNote();
        voiceNote.setId(VOICE_NOTE_ID);
        voiceNote.setOwnerId(uploaderId);
        voiceNote.setNoteId(NOTE_ID);
        voiceNote.setStorageKey("recordings/100.m4a");
        voiceNote.setContentType("audio/mp4");
        voiceNote.setOriginalFilename("reply.m4a");
        voiceNote.setSizeBytes(2048L);
        return voiceNote;
    }

    @Test
    void loadAudio_uploaderCanLoadTheirOwnRecording() {
        VoiceNote voiceNote = attachedRecording(NOTE_OWNER_ID);
        when(voiceNoteRepository.findById(VOICE_NOTE_ID)).thenReturn(Optional.of(voiceNote));
        Resource resource = mock(Resource.class);
        when(fileStorageService.load("recordings/100.m4a")).thenReturn(resource);

        VoiceNoteContent content = service.loadAudio(NOTE_OWNER_ID, VOICE_NOTE_ID);

        assertThat(content.resource()).isEqualTo(resource);
        // The uploader IS the owner — no need to ask NoteService anything.
        verify(noteService, never()).getNote(any(), any());
    }

    @Test
    void loadAudio_noteOwnerCanLoadACollaboratorsAttachedReply() {
        // Uploaded by the collaborator, attached to a note NOTE_OWNER_ID owns —
        // exactly the scenario from the bug report: playable in the list,
        // 404 on download, because loadAudio was still uploader-only.
        VoiceNote voiceNote = attachedRecording(COLLABORATOR_ID);
        when(voiceNoteRepository.findById(VOICE_NOTE_ID)).thenReturn(Optional.of(voiceNote));
        when(noteService.getNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(mock(NoteResponse.class));
        Resource resource = mock(Resource.class);
        when(fileStorageService.load("recordings/100.m4a")).thenReturn(resource);

        VoiceNoteContent content = service.loadAudio(NOTE_OWNER_ID, VOICE_NOTE_ID);

        assertThat(content.resource()).isEqualTo(resource);
        verify(noteService).getNote(NOTE_OWNER_ID, NOTE_ID);
    }

    @Test
    void loadAudio_strangerWhoOwnsNeitherTheNoteNorTheRecordingIsRejected() {
        VoiceNote voiceNote = attachedRecording(COLLABORATOR_ID);
        when(voiceNoteRepository.findById(VOICE_NOTE_ID)).thenReturn(Optional.of(voiceNote));
        // Not the uploader, and NoteService confirms they don't own the note either.
        when(noteService.getNote(STRANGER_ID, NOTE_ID)).thenThrow(new NoteNotFoundException("Note not found."));

        assertThatThrownBy(() -> service.loadAudio(STRANGER_ID, VOICE_NOTE_ID))
                .isInstanceOf(NoteNotFoundException.class);

        verify(fileStorageService, never()).load(any());
    }

    @Test
    void loadAudio_standaloneRecordingHasNoNoteFallback() {
        // noteId == null: nothing to fall back to. Must not ask NoteService
        // about a note that was never attached, and must reject cleanly.
        VoiceNote voiceNote = new VoiceNote();
        voiceNote.setId(VOICE_NOTE_ID);
        voiceNote.setOwnerId(COLLABORATOR_ID);
        voiceNote.setNoteId(null);
        voiceNote.setStorageKey("recordings/100.m4a");
        when(voiceNoteRepository.findById(VOICE_NOTE_ID)).thenReturn(Optional.of(voiceNote));

        assertThatThrownBy(() -> service.loadAudio(NOTE_OWNER_ID, VOICE_NOTE_ID))
                .isInstanceOf(VoiceNoteNotFoundException.class);

        verify(noteService, never()).getNote(any(), any());
        verify(fileStorageService, never()).load(any());
    }

    @Test
    void loadAudio_missingVoiceNoteIsNotFound() {
        when(voiceNoteRepository.findById(VOICE_NOTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadAudio(NOTE_OWNER_ID, VOICE_NOTE_ID))
                .isInstanceOf(VoiceNoteNotFoundException.class);
    }

    // -------------------------------------------------------------------
    // Step 9a — upload / delete / list.
    //
    // Fills the gaps left by the loadAudio-only suite above: upload's
    // validate-before-write ordering and its compensating delete on a
    // failed save, deleteVoiceNote's event-then-best-effort-storage-cleanup
    // ordering, and both list paths — including listVoiceNotesForNote's
    // documented fix (every recording attached to a note the caller owns,
    // not just the caller's own uploads).
    // -------------------------------------------------------------------

    private MultipartFile multipartFile(String contentType, String filename, byte[] bytes) {
        MultipartFile file = mock(MultipartFile.class);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new AssertionError(e); // unreachable — stubbing, not real I/O
        }
        when(file.getContentType()).thenReturn(contentType);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getSize()).thenReturn((long) bytes.length);
        return file;
    }

    @Test
    void upload_validatesBeforeTouchingStorageOrTheDatabase() {
        MultipartFile file = mock(MultipartFile.class);

        doThrow(new InvalidVoiceUploadException("Audio file is missing or empty."))
                .when(voiceUploadValidator).validate(file, 10);

        assertThatThrownBy(() -> service.upload(NOTE_OWNER_ID, file, NOTE_ID, 10))
                .isInstanceOf(InvalidVoiceUploadException.class);

        verifyNoInteractions(fileStorageService);
        verify(voiceNoteRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void upload_happyPath_storesBytesThenPersistsThenPublishesCreatedEvent() {
        MultipartFile file = multipartFile("audio/mpeg", "memo.mp3", new byte[]{1, 2, 3});
        when(fileStorageService.store(any(InputStream.class), eq("audio/mpeg"), eq("memo.mp3")))
                .thenReturn("recordings/abc.mp3");

        VoiceNote entity = new VoiceNote();
        when(voiceNoteMapper.toEntity(NOTE_OWNER_ID, NOTE_ID, "memo.mp3", "audio/mpeg",
                "recordings/abc.mp3", 3L, 10)).thenReturn(entity);

        VoiceNote saved = attachedRecording(NOTE_OWNER_ID);
        when(voiceNoteRepository.save(entity)).thenReturn(saved);

        VoiceNoteResponse response = mock(VoiceNoteResponse.class);
        when(voiceNoteMapper.toResponse(saved)).thenReturn(response);

        VoiceNoteResponse result = service.upload(NOTE_OWNER_ID, file, NOTE_ID, 10);

        assertThat(result).isEqualTo(response);
        verify(voiceUploadValidator).validate(file, 10);
        verify(eventPublisher).publishEvent(new VoiceNoteCreatedEvent(
                saved.getId(), saved.getOwnerId(), saved.getNoteId()));
        // Bytes must be stored before the row exists — an interrupted upload
        // must never leave a metadata row pointing at nothing.
        InOrder order = inOrder(fileStorageService, voiceNoteRepository);
        order.verify(fileStorageService).store(any(InputStream.class), anyString(), anyString());
        order.verify(voiceNoteRepository).save(entity);
    }

    @Test
    void upload_whenSaveFails_deletesTheOrphanedBytesThenRethrows() {
        MultipartFile file = multipartFile("audio/mpeg", "memo.mp3", new byte[]{1, 2, 3});
        when(fileStorageService.store(any(InputStream.class), anyString(), anyString()))
                .thenReturn("recordings/orphan.mp3");

        VoiceNote entity = new VoiceNote();
        when(voiceNoteMapper.toEntity(eq(NOTE_OWNER_ID), eq(NOTE_ID), eq("memo.mp3"), eq("audio/mpeg"),
                eq("recordings/orphan.mp3"), eq(3L), eq(10)))
                .thenReturn(entity);

        RuntimeException dbFailure = new RuntimeException("constraint violation");
        when(voiceNoteRepository.save(entity)).thenThrow(dbFailure);

        assertThatThrownBy(() -> service.upload(NOTE_OWNER_ID, file, NOTE_ID, 10))
                .isSameAs(dbFailure);

        // The file already exists on disk when save() fails, so it must be
        // cleaned up — otherwise it's an orphan with no metadata row.
        verify(fileStorageService).delete("recordings/orphan.mp3");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void upload_ioExceptionReadingTheFileBecomesAStorageException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("disconnected"));

        assertThatThrownBy(() -> service.upload(NOTE_OWNER_ID, file, NOTE_ID, 10))
                .isInstanceOf(StorageException.class);

        verify(voiceNoteRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteVoiceNote_ownerDeletesTheirOwnRecording_publishesEventThenBestEffortRemovesBytes() {
        VoiceNote vn = attachedRecording(NOTE_OWNER_ID);
        when(voiceNoteRepository.findByIdAndOwnerId(VOICE_NOTE_ID, NOTE_OWNER_ID)).thenReturn(Optional.of(vn));

        service.deleteVoiceNote(NOTE_OWNER_ID, VOICE_NOTE_ID);

        InOrder order = inOrder(voiceNoteRepository, eventPublisher, fileStorageService);
        // Metadata row goes first (deleting it is the source of truth for
        // "this voice note is gone"); the event fires right after, before the
        // best-effort storage cleanup, so a storage hiccup can never suppress it.
        order.verify(voiceNoteRepository).delete(vn);
        order.verify(eventPublisher).publishEvent(
                new VoiceNoteDeletedEvent(VOICE_NOTE_ID, "reply.m4a", NOTE_OWNER_ID));
        order.verify(fileStorageService).delete("recordings/100.m4a");
    }

    @Test
    void deleteVoiceNote_nonOwnerGetsNotFound_andNothingIsDeletedOrPublished() {
        when(voiceNoteRepository.findByIdAndOwnerId(VOICE_NOTE_ID, STRANGER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteVoiceNote(STRANGER_ID, VOICE_NOTE_ID))
                .isInstanceOf(VoiceNoteNotFoundException.class);

        verify(voiceNoteRepository, never()).delete(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void deleteVoiceNote_storageDeleteFailureIsSwallowed_rowIsStillGoneAndEventStillFired() {
        VoiceNote vn = attachedRecording(NOTE_OWNER_ID);
        when(voiceNoteRepository.findByIdAndOwnerId(VOICE_NOTE_ID, NOTE_OWNER_ID)).thenReturn(Optional.of(vn));
        doThrow(new RuntimeException("bucket unreachable"))
                .when(fileStorageService).delete("recordings/100.m4a");

        // Must not throw: the logical delete already succeeded, an orphaned
        // file is tolerable and left for a later cleanup sweep.
        service.deleteVoiceNote(NOTE_OWNER_ID, VOICE_NOTE_ID);

        verify(voiceNoteRepository).delete(vn);
        verify(eventPublisher).publishEvent(
                new VoiceNoteDeletedEvent(VOICE_NOTE_ID, "reply.m4a", NOTE_OWNER_ID));
    }

    @Test
    void listVoiceNotes_mapsEachPageEntryAndPreservesPagingMetadata() {
        Pageable pageable = PageRequest.of(0, 20);
        VoiceNote first = attachedRecording(NOTE_OWNER_ID);
        VoiceNote second = attachedRecording(NOTE_OWNER_ID);
        second.setId(101L);
        Page<VoiceNote> page = new PageImpl<>(List.of(first, second), pageable, 2);
        when(voiceNoteRepository.findByOwnerId(NOTE_OWNER_ID, pageable)).thenReturn(page);

        VoiceNoteResponse firstResponse = mock(VoiceNoteResponse.class);
        VoiceNoteResponse secondResponse = mock(VoiceNoteResponse.class);
        when(voiceNoteMapper.toResponse(first)).thenReturn(firstResponse);
        when(voiceNoteMapper.toResponse(second)).thenReturn(secondResponse);

        PagedResponse<VoiceNoteResponse> result = service.listVoiceNotes(NOTE_OWNER_ID, pageable);

        assertThat(result.content()).containsExactly(firstResponse, secondResponse);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void listVoiceNotesForNote_ownerSeesEveryAttachedRecording_includingACollaboratorsUpload() {
        // Regression coverage for the documented fix: findByNoteId (unscoped
        // by uploader) replaced findByOwnerIdAndNoteId, so the note owner
        // sees a WRITE collaborator's reply too, not only their own uploads.
        when(noteService.getNote(NOTE_OWNER_ID, NOTE_ID)).thenReturn(mock(NoteResponse.class));
        VoiceNote ownRecording = attachedRecording(NOTE_OWNER_ID);
        VoiceNote collaboratorsRecording = attachedRecording(COLLABORATOR_ID);
        collaboratorsRecording.setId(101L);
        when(voiceNoteRepository.findByNoteId(NOTE_ID))
                .thenReturn(List.of(ownRecording, collaboratorsRecording));

        VoiceNoteResponse ownResponse = mock(VoiceNoteResponse.class);
        VoiceNoteResponse collaboratorsResponse = mock(VoiceNoteResponse.class);
        when(voiceNoteMapper.toResponse(ownRecording)).thenReturn(ownResponse);
        when(voiceNoteMapper.toResponse(collaboratorsRecording)).thenReturn(collaboratorsResponse);

        List<VoiceNoteResponse> result = service.listVoiceNotesForNote(NOTE_OWNER_ID, NOTE_ID);

        assertThat(result).containsExactly(ownResponse, collaboratorsResponse);
        verify(voiceNoteRepository, never()).findByOwnerIdAndNoteId(any(), any());
    }

    @Test
    void listVoiceNotesForNote_callerWhoDoesNotOwnTheNoteIsRejectedBeforeAnyRecordingLookup() {
        when(noteService.getNote(STRANGER_ID, NOTE_ID)).thenThrow(new NoteNotFoundException("Note not found."));

        assertThatThrownBy(() -> service.listVoiceNotesForNote(STRANGER_ID, NOTE_ID))
                .isInstanceOf(NoteNotFoundException.class);

        verify(voiceNoteRepository, never()).findByNoteId(any());
    }
}
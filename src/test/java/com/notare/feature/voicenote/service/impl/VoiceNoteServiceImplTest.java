package com.notare.feature.voicenote.service.impl;

import com.notare.common.exception.NoteNotFoundException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.storage.FileStorageService;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.mapper.VoiceNoteMapper;
import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.repository.VoiceNoteRepository;
import com.notare.feature.voicenote.validation.VoiceUploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
}

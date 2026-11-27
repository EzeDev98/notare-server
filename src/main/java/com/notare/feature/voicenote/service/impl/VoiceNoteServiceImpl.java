package com.notare.feature.voicenote.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.StorageException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.storage.FileStorageService;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.ClipChange;
import com.notare.feature.voicenote.mapper.VoiceNoteMapper;
import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.repository.VoiceNoteRepository;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voicenote.validation.VoiceUploadValidator;
import lombok.RequiredArgsConstructor;
import com.notare.feature.voicenote.event.VoiceNoteDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Stores, reads and deletes a user's voice clips. */
@Service
@RequiredArgsConstructor
public class VoiceNoteServiceImpl implements VoiceNoteService {

    private final VoiceNoteRepository voiceNoteRepository;
    private final VoiceNoteMapper voiceNoteMapper;
    private final VoiceUploadValidator voiceUploadValidator;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    private final NoteService noteService;

    @Override
    public VoiceNoteResponse upload(Long ownerId, MultipartFile file, Long noteId, Integer durationSeconds) {
        return store(ownerId, noteId, null, file, durationSeconds).clip();
    }

    private VoiceUpload store(Long ownerId, Long noteId, String clientId, MultipartFile file, Integer durationSeconds) {
        if (clientId != null) {
            Optional<VoiceNote> existing = voiceNoteRepository.findByOwnerIdAndClientId(ownerId, clientId);
            if (existing.isPresent()) {
                return new VoiceUpload(voiceNoteMapper.toResponse(existing.get()), false);
            }
        }

        voiceUploadValidator.validate(file, durationSeconds);

        String storageKey = storeBytes(file);

        try {
            VoiceNote entity = voiceNoteMapper.toEntity(
                    ownerId, noteId,
                    file.getOriginalFilename(), file.getContentType(),
                    storageKey, file.getSize(), durationSeconds);
            entity.setClientId(clientId);
            VoiceNote saved = voiceNoteRepository.save(entity);
            return new VoiceUpload(voiceNoteMapper.toResponse(saved), true);
        } catch (DataIntegrityViolationException e) {
            fileStorageService.delete(storageKey);
            if (clientId == null) {
                throw e;
            }
            return voiceNoteRepository.findByOwnerIdAndClientId(ownerId, clientId)
                    .map(clip -> new VoiceUpload(voiceNoteMapper.toResponse(clip), false))
                    .orElseThrow(() -> e);
        } catch (RuntimeException e) {
            fileStorageService.delete(storageKey);
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean attachStandaloneClip(Long ownerId, Long clipId, Long noteId) {
        return voiceNoteRepository.attachStandalone(clipId, ownerId, noteId, java.time.LocalDateTime.now()) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteResponse getVoiceNote(Long ownerId, Long voiceNoteId) {
        return voiceNoteMapper.toResponse(requireOwned(ownerId, voiceNoteId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<VoiceNoteResponse> listVoiceNotes(Long ownerId, Pageable pageable) {
        Page<VoiceNote> page = voiceNoteRepository.findByOwnerId(ownerId, pageable);
        List<VoiceNoteResponse> content = page.getContent().stream()
                .map(voiceNoteMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceNoteResponse> listVoiceNotesForNote(Long ownerId, Long noteId) {
        noteService.getNote(ownerId, noteId);

        return voiceNoteRepository.findByNoteId(noteId).stream()
                .map(voiceNoteMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId) {
        VoiceNote voiceNote = voiceNoteRepository.findById(voiceNoteId)
                .orElseThrow(() -> new VoiceNoteNotFoundException("Voice note not found."));

        if (!voiceNote.getOwnerId().equals(ownerId)) {
            if (voiceNote.getNoteId() == null) {
                throw new VoiceNoteNotFoundException("Voice note not found.");
            }
            noteService.getNote(ownerId, voiceNote.getNoteId());
        }

        Resource resource = fileStorageService.load(voiceNote.getStorageKey());
        return new VoiceNoteContent(resource, voiceNote.getContentType(), voiceNote.getOriginalFilename(), voiceNote.getSizeBytes());
    }

    @Override
    public void deleteVoiceNote(Long ownerId, Long voiceNoteId) {
        VoiceNote vn = requireOwned(ownerId, voiceNoteId);
        String storageKey = vn.getStorageKey();

        Long deletedVoiceNoteId = vn.getId();
        String voiceNoteTitle = vn.getOriginalFilename();

        voiceNoteRepository.delete(vn);

        eventPublisher.publishEvent(new VoiceNoteDeletedEvent(deletedVoiceNoteId, voiceNoteTitle, ownerId));

        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClipChange> findClipChangesSince(Long ownerId, LocalDateTime since, Pageable pageable) {
        Pageable paging = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<VoiceNote> page = since == null
                ? voiceNoteRepository.findByOwnerIdAndNoteIdIsNotNullOrderByUpdatedAtAscIdAsc(ownerId, paging)
                : voiceNoteRepository.findClipChangesSince(ownerId, since, paging);
        return page.map(clip -> new ClipChange(
                clip.getId(), clip.getNoteId(), clip.getClientId(), clip.getDurationSeconds(),
                clip.getSizeBytes(), clip.getContentType(), clip.isDeleted()));
    }

    @Override
    public void removeClip(Long ownerId, Long clipId) {
        VoiceNote clip = requireOwned(ownerId, clipId);
        String storageKey = clip.getStorageKey();

        clip.setDeleted(true);
        clip.setRemoved(true);
        clip.setDeletedAt(LocalDateTime.now());
        voiceNoteRepository.save(clip);

        eventPublisher.publishEvent(new VoiceNoteDeletedEvent(clip.getId(), clip.getOriginalFilename(), ownerId));

        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    @Transactional
    public void touchClip(Long clipId) {
        voiceNoteRepository.touch(clipId, LocalDateTime.now());
    }

    private String storeBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return fileStorageService.store(in, file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new StorageException("Failed to read the uploaded file.", e);
        }
    }

    private VoiceNote requireOwned(Long ownerId, Long voiceNoteId) {
        return voiceNoteRepository.findByIdAndOwnerId(voiceNoteId, ownerId)
                .orElseThrow(() -> new VoiceNoteNotFoundException("Voice note not found."));
    }
}

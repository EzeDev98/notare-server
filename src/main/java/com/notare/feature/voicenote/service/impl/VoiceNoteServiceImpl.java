package com.notare.feature.voicenote.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.StorageException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.storage.FileStorageService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.mapper.VoiceNoteMapper;
import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.repository.VoiceNoteRepository;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voicenote.validation.VoiceUploadValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoiceNoteServiceImpl implements VoiceNoteService {

    private final VoiceNoteRepository voiceNoteRepository;
    private final VoiceNoteMapper voiceNoteMapper;
    private final VoiceUploadValidator voiceUploadValidator;
    private final FileStorageService fileStorageService;

    @Override
    public VoiceNoteResponse upload(Long ownerId, MultipartFile file, Long noteId, Integer durationSeconds) {
        // 1. Validate before writing anything anywhere.
        voiceUploadValidator.validate(file, durationSeconds);

        // 2. Store the bytes OUTSIDE any DB transaction. We must not hold a
        //    database connection open across (potentially slow) file I/O, and
        //    the filesystem/S3 is not part of a DB transaction anyway. This is
        //    why upload() is deliberately NOT annotated @Transactional.
        String storageKey = storeBytes(file);

        // 3. Persist the metadata row. The file already exists on disk now, so
        //    if this save fails the file would be orphaned — a row-less file.
        //    We compensate: delete the stored bytes, then rethrow. This is the
        //    saga / compensating-transaction idea in miniature, needed because
        //    a DB transaction cannot roll back a filesystem write.
        try {
            VoiceNote entity = voiceNoteMapper.toEntity(
                    ownerId, noteId,
                    file.getOriginalFilename(), file.getContentType(),
                    storageKey, file.getSize(), durationSeconds);
            VoiceNote saved = voiceNoteRepository.save(entity);
            return voiceNoteMapper.toResponse(saved);
        } catch (RuntimeException e) {
            fileStorageService.delete(storageKey);
            throw e;
        }
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
        return voiceNoteRepository.findByOwnerIdAndNoteId(ownerId, noteId).stream()
                .map(voiceNoteMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId) {
        VoiceNote voiceNote = requireOwned(ownerId, voiceNoteId);
        Resource resource = fileStorageService.load(voiceNote.getStorageKey());
        return new VoiceNoteContent(resource, voiceNote.getContentType(), voiceNote.getOriginalFilename(), voiceNote.getSizeBytes());
    }

    @Override
    public void deleteVoiceNote(Long ownerId, Long voiceNoteId) {
        VoiceNote vn = requireOwned(ownerId, voiceNoteId);
        String storageKey = vn.getStorageKey();

        // Remove the metadata row first (Spring Data's delete is its own
        // transaction). Once that commits, nothing references the file, so
        // deleting the bytes second means a failure here leaves at most a
        // harmless orphaned file — never a row pointing at missing bytes.
        voiceNoteRepository.delete(vn);

        // Best-effort: the logical delete already succeeded, so a storage hiccup
        // must not fail the request. Worst case is a leftover file for a later
        // cleanup sweep. (For multi-step DB transactions, the more correct tool
        // is a TransactionSynchronization afterCommit hook — worth knowing.)
        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException ignored) {
            // swallow — orphaned file is tolerable; the note is gone as requested
        }
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
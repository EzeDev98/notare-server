package com.notare.feature.sync.service.impl;

import com.notare.feature.note.dto.response.NoteChange;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.sync.config.SyncProperties;
import com.notare.feature.sync.dto.response.SyncClipResponse;
import com.notare.feature.sync.dto.response.SyncNoteResponse;
import com.notare.feature.sync.dto.response.SyncPullResponse;
import com.notare.feature.sync.model.NoteTombstone;
import com.notare.feature.sync.repository.NoteTombstoneRepository;
import com.notare.feature.sync.service.SyncPullService;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.service.TranscriptionService;
import com.notare.feature.voicenote.dto.response.ClipChange;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Finds what changed for a user since their last sync. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncPullServiceImpl implements SyncPullService {

    private final NoteService noteService;
    private final NoteTombstoneRepository noteTombstoneRepository;
    private final SyncProperties syncProperties;
    private final VoiceNoteService voiceNoteService;
    private final TranscriptionService transcriptionService;

    @Override
    @Transactional(readOnly = true)
    public SyncPullResponse pull(Long ownerId, LocalDateTime since, Pageable pageable) {

        LocalDateTime snapshot = LocalDateTime.now();

        LocalDateTime effectiveSince = (since == null)
                ? null
                : since.minusSeconds(syncProperties.getOverlapSeconds());

        Pageable window = clamp(pageable);
        Page<NoteChange> page = noteService.findChangesSince(ownerId, effectiveSince, window);

        Page<ClipChange> clipPage = voiceNoteService.findClipChangesSince(ownerId, effectiveSince, window);
        List<SyncClipResponse> clips = withTranscripts(clipPage.getContent());

        List<SyncNoteResponse> changed = new ArrayList<>();
        List<Long> deletedIds = new ArrayList<>();

        for (NoteChange change : page.getContent()) {
            if (change.deleted()) {
                deletedIds.add(change.noteId());
            } else {
                changed.add(new SyncNoteResponse(change.note(), change.clientId()));
            }
        }

        if (effectiveSince != null) {
            List<NoteTombstone> tombstones = noteTombstoneRepository
                    .findByOwnerIdAndDeletedAtGreaterThanEqual(ownerId, effectiveSince);

            if (tombstones.size() > syncProperties.getMaxPageSize()) {
                log.info("Large tombstone batch for owner {}: {} entries", ownerId, tombstones.size());
            }
            tombstones.forEach(tombstone -> deletedIds.add(tombstone.getNoteId()));
        }

        boolean hasMore = page.hasNext() || clipPage.hasNext();

        LocalDateTime nextCursor = hasMore ? since : snapshot;

        return new SyncPullResponse(changed, deletedIds, nextCursor, hasMore, clips);
    }

    private List<SyncClipResponse> withTranscripts(List<ClipChange> changes) {
        List<Long> visible = changes.stream().filter(change -> !change.deleted()).map(ClipChange::id).toList();
        Map<Long, TranscriptionResponse> transcripts = transcriptionService.getForVoiceNotes(visible);

        return changes.stream().map(change -> {
            TranscriptionResponse found = change.deleted() ? null : transcripts.get(change.id());
            SyncClipResponse.Transcript transcript = found == null
                    ? null
                    : new SyncClipResponse.Transcript(found.status(), found.transcript(), found.language());
            return new SyncClipResponse(change.id(), change.noteId(), change.clientId(), change.durationSeconds(),
                    change.sizeBytes(), change.contentType(), change.deleted(), transcript);
        }).toList();
    }

    private Pageable clamp(Pageable pageable) {
        int requested = pageable.isPaged()
                ? pageable.getPageSize()
                : syncProperties.getDefaultPageSize();
        int size = Math.min(requested, syncProperties.getMaxPageSize());
        int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
        return PageRequest.of(pageNumber, size);
    }
}

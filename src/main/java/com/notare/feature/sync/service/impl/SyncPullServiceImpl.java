package com.notare.feature.sync.service.impl;

import com.notare.feature.note.dto.response.NoteChange;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.sync.config.SyncProperties;
import com.notare.feature.sync.dto.response.SyncNoteResponse;
import com.notare.feature.sync.dto.response.SyncPullResponse;
import com.notare.feature.sync.model.NoteTombstone;
import com.notare.feature.sync.repository.NoteTombstoneRepository;
import com.notare.feature.sync.service.SyncPullService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncPullServiceImpl implements SyncPullService {

    private final NoteService noteService;
    private final NoteTombstoneRepository noteTombstoneRepository;
    private final SyncProperties syncProperties;

    @Override
    @Transactional(readOnly = true)
    public SyncPullResponse pull(Long ownerId, LocalDateTime since, Pageable pageable) {

        // Snapshot the clock BEFORE querying, and hand THIS back as the next
        // cursor. Taking it afterwards would be a bug: a note written while we
        // were paging would carry an updatedAt earlier than the cursor we
        // returned, so the client would never ask for it again. Anything
        // modified from this instant onward has updatedAt >= snapshot and is
        // therefore caught by the next pull.
        LocalDateTime snapshot = LocalDateTime.now();

        // Rewind the cursor to create the overlap window. A transaction that
        // started before the client's cursor was issued but committed after it
        // sits in the gap between two windows; without this it would never be
        // sent again, and nothing anywhere would report a problem. Re-reading a
        // few seconds of rows the client already has is the cheap, visible,
        // recoverable failure mode.
        LocalDateTime effectiveSince = (since == null)
                ? null
                : since.minusSeconds(syncProperties.getOverlapSeconds());

        Page<NoteChange> page = noteService.findChangesSince(ownerId, effectiveSince, clamp(pageable));

        // Split into the two lists the client uses differently: merge these,
        // evict those.
        List<SyncNoteResponse> changed = new ArrayList<>();
        List<Long> deletedIds = new ArrayList<>();

        for (NoteChange change : page.getContent()) {
            if (change.deleted()) {
                deletedIds.add(change.noteId());
            } else {
                changed.add(new SyncNoteResponse(change.note(), change.clientId()));
            }
        }

        // Tombstones for notes that were PERMANENTLY deleted. Soft-deleted notes
        // already arrived above as rows; these left no row at all, which is
        // exactly why the tombstone table exists.
        //
        // Only on an incremental sync. A client with no cursor has nothing
        // cached to evict, so sending it the graveyard would be pure noise —
        // and on a busy account it could dwarf the actual notes.
        if (effectiveSince != null) {
            List<NoteTombstone> tombstones = noteTombstoneRepository
                    .findByOwnerIdAndDeletedAtGreaterThanEqual(ownerId, effectiveSince);

            if (tombstones.size() > syncProperties.getMaxPageSize()) {
                // Not fatal — these are bare ids and the client handles them
                // fine. Worth a line in the log though: it means someone deleted
                // an unusual amount while a device was away, and if it becomes
                // routine this list needs its own paging strategy.
                log.info("Large tombstone batch for owner {}: {} entries", ownerId, tombstones.size());
            }
            tombstones.forEach(tombstone -> deletedIds.add(tombstone.getNoteId()));
        }

        // hasMore reflects the NOTE page only. Tombstones are returned whole, so
        // there is never a partial tombstone set to come back for.
        boolean hasMore = page.hasNext();

        // While more pages remain, hand back the SAME cursor the client sent.
        //
        // This is the part that's easy to get wrong. The tempting design is to
        // return the last row's updatedAt and let the client walk forward on
        // timestamps — but with a >= comparison, a page filled entirely by rows
        // sharing one timestamp returns the identical page forever. A bulk
        // import can produce exactly that, and the client loops.
        //
        // Holding `since` fixed and paging by NUMBER within that window sidesteps
        // it completely: the window is stable, the sort is total, and the cursor
        // only advances once the whole window has been drained. The snapshot
        // taken above then covers anything written during the walk.
        LocalDateTime nextCursor = hasMore ? since : snapshot;

        return new SyncPullResponse(changed, deletedIds, nextCursor, hasMore);
    }

    /**
     * Applies the configured default and ceiling to the requested page size.
     *
     * <p>The ceiling matters: a pull is one query, but an unbounded page size
     * lets a single client ask the server to materialise and serialise its
     * entire note table in one response. Sort is left alone here because the
     * note module discards and rebuilds it anyway — correctness lives with the
     * query, not with the caller.
     */
    private Pageable clamp(Pageable pageable) {
        int requested = pageable.isPaged()
                ? pageable.getPageSize()
                : syncProperties.getDefaultPageSize();
        int size = Math.min(requested, syncProperties.getMaxPageSize());
        int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
        return PageRequest.of(pageNumber, size);
    }
}
package com.notare.feature.collab.service.impl;

import com.notare.feature.collab.model.NoteSnapshot;
import com.notare.feature.collab.model.NoteUpdate;
import com.notare.feature.collab.repository.NoteSnapshotRepository;
import com.notare.feature.collab.repository.NoteUpdateRepository;
import com.notare.feature.collab.service.CollabStoreService;
import com.notare.feature.note.enums.BodyMode;
import com.notare.feature.note.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CollabStoreServiceImpl implements CollabStoreService {

    private final NoteUpdateRepository updates;
    private final NoteSnapshotRepository snapshots;
    private final NoteRepository notes;

    @Override
    @Transactional
    public long appendUpdate(Long noteId, Long authorId, byte[] payload) {
        NoteUpdate row = new NoteUpdate();
        row.setNoteId(noteId);
        row.setSeq(headOf(noteId) + 1);
        row.setPayload(payload);
        row.setAuthorId(authorId);
        updates.save(row);

        notes.findById(noteId)
                .filter(note -> note.getBodyMode() != BodyMode.COLLAB)
                .ifPresent(note -> {
                    note.setBodyMode(BodyMode.COLLAB);
                    notes.save(note);
                });

        return row.getSeq();
    }

    @Override
    @Transactional(readOnly = true)
    public long head(Long noteId) {
        return headOf(noteId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NoteSnapshot> snapshot(Long noteId) {
        return snapshots.findById(noteId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteUpdate> updatesAfter(Long noteId, long seq) {
        return updates.findByNoteIdAndSeqGreaterThanOrderBySeqAsc(noteId, seq);
    }

    @Override
    @Transactional
    public SnapshotResult saveSnapshot(Long noteId, long upToSeq, byte[] state, String plainText) {
        if (upToSeq > headOf(noteId)) {
            return SnapshotResult.AHEAD;
        }

        NoteSnapshot existing = snapshots.findById(noteId).orElse(null);
        if (existing != null && upToSeq <= existing.getUpToSeq()) {
            return SnapshotResult.STALE;
        }

        NoteSnapshot snapshot = existing != null ? existing : new NoteSnapshot();
        snapshot.setNoteId(noteId);
        snapshot.setState(state);
        snapshot.setUpToSeq(upToSeq);
        snapshot.setPlainText(plainText);
        snapshots.save(snapshot);

        updates.deleteUpTo(noteId, upToSeq);

        // The text that search, notification labels, shared views and the sync pull read
        // is notes.content. Mirroring the snapshot's plain text into it means every one
        // of those readers shows the current text, never the stale pre-collaboration
        // body, without any of them knowing about snapshots (step 15f). A change here
        // bumps the note's version and updatedAt, so devices syncing see it. Setting
        // the same text again changes nothing and writes nothing.
        notes.findById(noteId).ifPresent(note -> {
            note.setContent(plainText);
            notes.save(note);
        });

        return SnapshotResult.SAVED;
    }

    /**
     * Compaction deletes updates but never lowers the head, because the snapshot
     * remembers the seq it covers. Without that, seq would restart after a
     * compaction and a client's lastSeq would point at the wrong update.
     */
    private long headOf(Long noteId) {
        long stored = updates.maxSeq(noteId);
        long compacted = snapshots.findById(noteId).map(NoteSnapshot::getUpToSeq).orElse(0L);
        return Math.max(stored, compacted);
    }
}

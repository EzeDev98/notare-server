package com.notare.feature.collab.service;

import com.notare.feature.collab.model.NoteSnapshot;
import com.notare.feature.collab.model.NoteUpdate;

import java.util.List;
import java.util.Optional;

/**
 * What the collaboration socket reads and writes (step 15d). The server never
 * looks inside the bytes: it numbers updates, stores them and compacts them.
 */
public interface CollabStoreService {

    enum SnapshotResult {
        /** Stored; updates up to upToSeq were deleted. */
        SAVED,
        /** Older than the snapshot already stored. Ignored, nothing is wrong. */
        STALE,
        /** Claims to include updates the server never numbered. Rejected. */
        AHEAD
    }

    /**
     * Stores an update as the note's next seq and returns that seq. The first
     * update turns a LEGACY note into a COLLAB one (D-032). Callers serialise per
     * note; the unique (note_id, seq) constraint is the backstop.
     */
    long appendUpdate(Long noteId, Long authorId, byte[] payload);

    /** The last seq handed out for the note: stored updates or the snapshot, whichever is higher. */
    long head(Long noteId);

    Optional<NoteSnapshot> snapshot(Long noteId);

    List<NoteUpdate> updatesAfter(Long noteId, long seq);

    SnapshotResult saveSnapshot(Long noteId, long upToSeq, byte[] state, String plainText);
}

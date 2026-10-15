package com.notare.feature.note.enums;

/**
 * Where a note's body lives (step 15b).
 *
 * <p>{@link #LEGACY}: the body is {@code notes.content}, written by the old
 * PUT/PATCH endpoints and the legacy sync.
 *
 * <p>{@link #COLLAB}: the body is a Yjs document kept as updates and snapshots.
 * A note becomes COLLAB when its first update arrives and never goes back, so a
 * content write from an old client can never overwrite a merged body (15f).
 */
public enum BodyMode {
    LEGACY,
    COLLAB
}

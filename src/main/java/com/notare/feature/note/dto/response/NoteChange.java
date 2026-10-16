package com.notare.feature.note.dto.response;

/**
 * One row of delta sync, as the NOTE module hands it out.
 *
 * <p>This type exists to keep a boundary intact. The sync module needs to know
 * which notes changed AND which were trashed — but "trashed" is expressed by
 * the Note entity's deleted flag, which NoteResponse deliberately hides (a
 * Step 1 decision: that DTO describes an ACTIVE note). The alternatives were to
 * let the sync module query NoteRepository directly, breaking the rule that
 * cross-module access goes through a public service, or to widen NoteResponse
 * and leak internal state into every note payload in the app.
 *
 * <p>So the note module answers the delta question in its own vocabulary and
 * the sync module reshapes the answer. Same move as VoiceNoteContent in
 * Step 4 — a small carrier type is cheaper than either broken boundary.
 *
 * <p>Note that this lives in the NOTE module and mentions nothing about sync.
 * "What of mine changed since X, including what I trashed" is a reasonable
 * question to ask about notes; it just happens that sync is the only caller
 * today.
 */
public record NoteChange(

        /** Always present, including for a tombstone — it's all an eviction needs. */
        Long noteId,

        /** True when the note is in the trash. The two fields below are then null. */
        boolean deleted,

        /** The note, or null for a tombstone. No point shipping the content of something the user just trashed. */
        NoteResponse note,

        /** The client-generated id this note was created with, or null if it was created online. */
        String clientId,

        /** For a tombstone: when the trash job deletes the note for good (step 17e). Null otherwise. */
        java.time.LocalDateTime purgeAt
) {

    public NoteChange(Long noteId, boolean deleted, NoteResponse note, String clientId) {
        this(noteId, deleted, note, clientId, null);
    }
}
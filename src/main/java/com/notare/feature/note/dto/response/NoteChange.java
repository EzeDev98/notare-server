package com.notare.feature.note.dto.response;

/** One changed note for the sync pull. */
public record NoteChange(

        Long noteId,

        boolean deleted,

        NoteResponse note,

        String clientId,

        java.time.LocalDateTime purgeAt
) {

    public NoteChange(Long noteId, boolean deleted, NoteResponse note, String clientId) {
        this(noteId, deleted, note, clientId, null);
    }
}

package com.notare.feature.sync.dto.response;

import com.notare.feature.note.dto.response.NoteResponse;

/** A changed note as the sync pull delivers it. */
public record SyncNoteResponse(

        NoteResponse note,

        String clientId
) {
}

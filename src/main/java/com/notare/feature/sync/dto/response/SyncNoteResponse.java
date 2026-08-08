package com.notare.feature.sync.dto.response;

import com.notare.feature.note.dto.response.NoteResponse;

/**
 * A changed note as delta sync delivers it: the ordinary note, plus the one
 * thing a syncing client needs that the ordinary DTO doesn't carry.
 *
 * <p>NoteResponse is NESTED rather than flattened, the same call made for
 * SharedNoteSummaryResponse in Step 7: the note module keeps sole ownership of
 * a note's wire shape, so a field added there shows up here automatically
 * instead of being forgotten in a parallel copy.
 */
public record SyncNoteResponse(

        NoteResponse note,

        /**
         * The client id this note was created with, or null if it was created
         * online.
         *
         * <p>It's here for one specific failure: a device creates a note
         * offline, the batch applies, and the RESPONSE is lost. The server has
         * the note; the device still holds an unreconciled local copy with only
         * its clientId. On the next pull it would see an unfamiliar server note
         * and create a duplicate — its own note, arriving as a stranger.
         * Carrying clientId lets it recognise the note as the one it made.
         *
         * <p>The batch ledger covers the same failure when the client retries
         * the operation; this covers it when the client pulls instead. Two
         * routes to the same reconciliation, because a client may take either.
         */
        String clientId
) {
}
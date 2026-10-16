package com.notare.feature.note.dto.response;

import java.time.LocalDateTime;

/**
 * A note in the trash: the ordinary summary, plus when it was trashed.
 *
 * <h2>Why deletedAt is not simply added to NoteSummaryResponse</h2>
 *
 * <p>{@link NoteSummaryResponse} describes an ACTIVE note, and its Step 1
 * writeup says so: it exposes no {@code deleted} and no {@code deletedAt}
 * because on every other endpoint those would be constants — false and null —
 * shipped on every row of every page load.
 *
 * <p>Widening it would put a field on the notes list, the search results, the
 * shared-with-me list and the sync feed that is meaningful on exactly one
 * screen. Nesting instead keeps the note module's summary shape owned in one
 * place: add a field to a note summary and it appears here automatically,
 * rather than being forgotten in a parallel copy that slowly drifts.
 *
 * <p>Same call as SharedNoteSummaryResponse in Step 7 and SyncNoteResponse in
 * Step 9 — this row IS a note plus one piece of context, and the JSON says so.
 *
 * <h2>What deletedAt is for, and what it is not</h2>
 *
 * <p>It lets the client sort and label — "trashed 3 days ago". It is
 * <b>not</b> the basis for a countdown. Nothing in this application purges
 * trashed notes: {@code SyncRetentionJob} sweeps the sync ledger and tombstones
 * and touches the notes table not at all. A UI promising "permanently deleted
 * in 30 days" would be lying, and the lie is one users act on.
 */
public record TrashedNoteResponse(
        /** The note, in the same shape the owner's own list endpoint returns. */
        NoteSummaryResponse note,

        /**
         * When it went to the trash. Never null in practice — the query only
         * returns rows with deleted = true, and softDeleteNote sets both
         * together — but nullable in the column, because rows predating the
         * field would have it empty.
         */
        LocalDateTime deletedAt,

        /** When the trash job deletes it for good (step 17e). The client counts days left from this. */
        LocalDateTime purgeAt
) {

    public TrashedNoteResponse(NoteSummaryResponse note, LocalDateTime deletedAt) {
        this(note, deletedAt, null);
    }
}
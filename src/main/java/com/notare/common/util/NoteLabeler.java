package com.notare.common.util;

/**
 * Derives a human-readable name for a note from its content.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A note is identified everywhere by the first non-blank line of its
 * content, mirroring the client's own {@code deriveNoteLabel}. This is the
 * server-side half of that same rule — used where a share notification needs
 * to snapshot a readable name for a note at the moment something happened to
 * it (see NoteSharedEvent, NoteSharePermissionChangedEvent,
 * NoteShareRevokedEvent).
 *
 * <p>There is deliberately ONE implementation, not one per call site — three
 * event publishers in NoteShareServiceImpl all need this, and hand-rolled
 * copies of "trim, split on newline, find the first non-blank line" are
 * exactly the kind of logic that quietly drifts apart across copies.
 *
 * <h2>The 255-character cap</h2>
 *
 * <p>The client's version never truncates — it only ever renders into a UI
 * that can wrap or ellipsis on its own. This method feeds
 * {@code Notification.targetLabel}, a {@code VARCHAR(255)} column, and
 * content has no length limit of its own, so an unbroken first line longer
 * than the column would fail the insert outright. The label is truncated
 * here rather than at the last possible moment where a caller could forget
 * to.
 */
public final class NoteLabeler {

    /** Matches the client's EMPTY_NOTE_LABEL exactly — same rule, same wording, both sides of the wire. */
    public static final String EMPTY_NOTE_LABEL = "New Note";

    private static final int MAX_LENGTH = 255;
    private static final String ELLIPSIS = "…";

    private NoteLabeler() {
    }

    /**
     * The first non-blank line of {@code content}, trimmed and truncated to
     * fit a 255-character column. {@link #EMPTY_NOTE_LABEL} when there is no
     * content, or every line is blank.
     */
    public static String deriveLabel(String content) {
        if (content == null) {
            return EMPTY_NOTE_LABEL;
        }

        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return EMPTY_NOTE_LABEL;
        }

        for (String line : trimmed.split("\n", -1)) {
            String candidate = line.trim();
            if (!candidate.isEmpty()) {
                return truncate(candidate);
            }
        }

        // Every line was blank (e.g. content was just "\n   \n") even though
        // trimmed itself wasn't empty — same outcome as no content at all.
        return EMPTY_NOTE_LABEL;
    }

    private static String truncate(String label) {
        if (label.length() <= MAX_LENGTH) {
            return label;
        }
        return label.substring(0, MAX_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}

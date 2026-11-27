package com.notare.common.util;

/** Derives a readable name for a note from its content. */
public final class NoteLabeler {

    public static final String EMPTY_NOTE_LABEL = "New Note";

    private static final int MAX_LENGTH = 255;
    private static final String ELLIPSIS = "…";

    private NoteLabeler() {
    }

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

        return EMPTY_NOTE_LABEL;
    }

    private static String truncate(String label) {
        if (label.length() <= MAX_LENGTH) {
            return label;
        }
        return label.substring(0, MAX_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}

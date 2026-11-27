package com.notare.common.util;

/** Makes a search term safe to use in a SQL LIKE pattern. */
public final class LikeEscaper {

    public static final String ESCAPE_CHARACTER = "!";

    private LikeEscaper() {
    }

    public static String escape(String term) {
        if (term == null || term.isEmpty()) {
            return term;
        }

        return term
                .replace(ESCAPE_CHARACTER, ESCAPE_CHARACTER + ESCAPE_CHARACTER)
                .replace("%", ESCAPE_CHARACTER + "%")
                .replace("_", ESCAPE_CHARACTER + "_");
    }
}

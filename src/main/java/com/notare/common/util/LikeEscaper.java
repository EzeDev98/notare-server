package com.notare.common.util;

/**
 * Makes a user-supplied search term safe to drop into a SQL LIKE pattern.
 *
 * <h2>The problem</h2>
 *
 * <p>Every search in this application wraps the term in wildcards:
 * {@code LIKE CONCAT('%', :term, '%')}. That is fine until the term itself
 * contains a wildcard. {@code %} matches any run of characters and {@code _}
 * matches exactly one, so:
 *
 * <ul>
 *   <li>searching notes for {@code %} matches EVERY note, because the pattern
 *       becomes {@code '%%%'}</li>
 *   <li>searching for {@code snake_case} also matches {@code snakeXcase}</li>
 *   <li>searching users for {@code _} matches every username of any length</li>
 * </ul>
 *
 * <p>None of this is SQL injection — the term is still a bound parameter and
 * cannot change the query's structure. It is a correctness bug, and on user
 * search a mild privacy one: a single {@code %} turns an endpoint that
 * deliberately refuses to list the directory into one that lists it. That
 * endpoint returns an empty page for a blank term precisely to avoid handing
 * out everyone's name; a one-character wildcard walks straight around the
 * check.
 *
 * <h2>The fix, and why the escape character is '!'</h2>
 *
 * <p>SQL's {@code ESCAPE} clause designates a character that makes the next
 * one literal. The conventional choice is a backslash — and it is the wrong
 * choice here. JPA does not define backslash handling inside JPQL string
 * literals, so {@code ESCAPE '\'} is interpreted differently by different
 * Hibernate versions and dialects, and the failure is silent: the query still
 * runs, it just stops escaping. {@code '!'} has no special meaning in JPQL,
 * SQL, or any dialect this application targets, so it means exactly one thing
 * everywhere.
 *
 * <h2>Order matters</h2>
 *
 * <p>The escape character is escaped FIRST. Reversing it corrupts the output:
 * escaping {@code %} first turns {@code %} into {@code !%}, and the following
 * pass over {@code !} would then turn that into {@code !!%} — a literal '!'
 * followed by a live wildcard. The introduced escapes must be in place before
 * anything can add more.
 *
 * <p>Escaping the escape character is also not optional for a second reason:
 * an unpaired {@code !} in a pattern is undefined behaviour in the standard
 * and an outright error in some databases. After this method every {@code !}
 * in the pattern is part of a valid two-character sequence.
 */
public final class LikeEscaper {

    /**
     * The ESCAPE character. Must match the {@code ESCAPE '!'} clause on every
     * LIKE query that receives an escaped term — the two are a pair, and
     * changing one without the other silently disables escaping rather than
     * failing.
     */
    public static final String ESCAPE_CHARACTER = "!";

    private LikeEscaper() {
    }

    /**
     * Neutralises LIKE wildcards in a user-supplied term.
     *
     * <p>Null and blank pass through unchanged. Blank in particular must keep
     * its meaning: note and transcript search treat an empty term as "match
     * everything" and user search treats it as "return nothing", and both
     * decisions are made by the caller before this is reached.
     */
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
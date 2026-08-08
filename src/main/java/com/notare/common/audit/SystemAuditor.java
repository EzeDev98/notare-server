package com.notare.common.audit;

/**
 * The reserved auditor id used when a write has no authenticated user behind it.
 *
 * <p>Three contexts in this application write to the database with no principal
 * available: the transcription worker (an @Async thread — SecurityContextHolder
 * is thread-local and the context is NOT inherited), the sync retention job (a
 * scheduler thread, same reason), and registration itself (the User row is
 * written before anyone could possibly be authenticated).
 *
 * <p>The obvious alternative is to leave the column null. It's rejected because
 * null would then carry two meanings at once — "the system did this" and
 * "auditing is misconfigured or this row predates it" — and no query could tell
 * them apart afterwards. An audit trail whose most common value is ambiguous is
 * not much of an audit trail. One reserved id says exactly one thing.
 *
 * <p>This is only safe because of a decision made back in Step 1: audit columns
 * are plain Longs with no foreign key to the users table, so an id matching no
 * real user violates nothing. With @ManyToOne relationships this approach would
 * be impossible and null would be the only option.
 *
 * <p>0 is chosen because IDENTITY sequences start at 1, so it can never collide
 * with a real user. Keep it out of any user-facing display — a client resolving
 * auditor ids to names must special-case it rather than showing "user 0".
 */
public final class SystemAuditor {

    /** The id recorded for any write not attributable to a logged-in person. */
    public static final Long ID = 0L;

    private SystemAuditor() {
        // Constants holder — never instantiated.
    }
}
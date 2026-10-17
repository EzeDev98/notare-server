package com.notare.feature.notification.enums;

/**
 * What happened. One constant per thing the app tells a user about.
 *
 * <p>The type is the notification's stable identity — it's what the client
 * switches on to pick an icon, a phrasing, and a destination. Everything else
 * on the row is detail hanging off it.
 *
 * <p>Named for the EVENT, not the message ("NOTE_SHARED", not
 * "SOMEONE_SHARED_A_NOTE_WITH_YOU"). The wording of the sentence is a
 * presentation concern that will change, get translated, and differ between web
 * and mobile; the fact that a note was shared will not. Baking phrasing into a
 * persisted enum means old rows carry old wording forever.
 *
 * <p>Stored as @Enumerated(STRING), so this list can be reordered or extended
 * without reinterpreting existing rows. Adding a constant is safe; REMOVING one
 * is not — historical rows still reference it, and Hibernate will fail to read
 * them. Retire a type by ceasing to create it, not by deleting the constant.
 */
public enum NotificationType {

    /** Someone invited me to collaborate (Step 6). Actor: the inviter. */
    COLLABORATION_INVITED,

    /** Someone accepted my invitation. Actor: the invitee. */
    COLLABORATION_ACCEPTED,

    /** Someone declined my invitation. Actor: the invitee. */
    COLLABORATION_REJECTED,

    /**
     * Someone removed me from their connections (step 17i). Actor: the owner who removed me. Sent only
     * for an accepted connection; withdrawing a pending invitation tells no one. No target: the
     * connection no longer exists.
     */
    COLLABORATION_REMOVED,

    /** A note was shared with me (Step 7). Actor: the note's owner. */
    NOTE_SHARED,

    /** My access level on a shared note changed. Actor: the note's owner. */
    NOTE_SHARE_PERMISSION_CHANGED,

    /** My access to a shared note was withdrawn. Actor: the note's owner. */
    NOTE_SHARE_REVOKED,

    /** My voice note finished transcribing (Step 5). No actor — the system did it. */
    TRANSCRIPTION_COMPLETED,

    /** My voice note failed to transcribe. No actor. */
    TRANSCRIPTION_FAILED,

    /** A voice note was shared with me. Actor: the voice note's owner. */
    VOICE_NOTE_SHARED,

    /** My access level on a shared voice note changed. Actor: the voice note's owner. */
    VOICE_NOTE_SHARE_PERMISSION_CHANGED,

    /** My access to a shared voice note was withdrawn. Actor: the voice note's owner. */
    VOICE_NOTE_SHARE_REVOKED
}
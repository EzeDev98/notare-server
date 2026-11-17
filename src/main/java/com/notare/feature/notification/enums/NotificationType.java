package com.notare.feature.notification.enums;

/**
 * What happened. One constant per thing the app tells a user about.
 *
 * <p>The type is the notification's stable identity — it's what the client
 * switches on to pick an icon, a phrasing, and a destination. Everything else
 * on the row is detail hanging off it.
 *
 * <p>Named for the EVENT, not the message ("TRANSCRIPTION_COMPLETED", not
 * "YOUR_TRANSCRIPT_IS_READY"). The wording of the sentence is a presentation
 * concern that will change, get translated, and differ between web and mobile;
 * the fact that a transcription completed will not. Baking phrasing into a
 * persisted enum means old rows carry old wording forever.
 *
 * <p>Stored as @Enumerated(STRING), so this list can be reordered or extended
 * without reinterpreting existing rows. Adding a constant is safe; REMOVING one
 * is not — historical rows still reference it, and Hibernate will fail to read
 * them. Retire a type by ceasing to create it, or delete its rows in a migration
 * first (V13 did that for the collaboration and sharing types).
 */
public enum NotificationType {

    /** My voice note finished transcribing (Step 5). No actor — the system did it. */
    TRANSCRIPTION_COMPLETED,

    /** My voice note failed to transcribe. No actor. */
    TRANSCRIPTION_FAILED
}

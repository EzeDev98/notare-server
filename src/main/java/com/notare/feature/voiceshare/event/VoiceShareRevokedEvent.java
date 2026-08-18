package com.notare.feature.voiceshare.event;

/**
 * Access to a shared voice note was withdrawn. Notify the RECIPIENT.
 *
 * <p>Mirrors {@link com.notare.feature.share.event.NoteShareRevokedEvent}.
 * The title snapshot is captured before the grant is deleted — same reason:
 * it's the only way the recipient can be told which voice note they lost,
 * since they can no longer fetch it once the grant is gone.
 *
 * <p>Distinct from CollaborationRemovedEvent, same as the note-share
 * equivalent: that one ends a relationship and cascades grant deletion (Step
 * 6c); this one is a single grant withdrawn by hand.
 */
public record VoiceShareRevokedEvent(
        Long voiceNoteId,
        String voiceNoteTitle,
        Long ownerId,
        Long sharedWithUserId
) {
}

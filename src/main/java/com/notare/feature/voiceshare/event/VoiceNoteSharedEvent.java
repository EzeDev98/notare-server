package com.notare.feature.voiceshare.event;

import com.notare.feature.share.enums.SharePermission;

/**
 * A voice note was shared with someone. Notify the RECIPIENT.
 *
 * <p>Mirrors {@link com.notare.feature.share.event.NoteSharedEvent} exactly.
 * originalFilename plays the title's role here — it's the snapshot the
 * notification stores, captured at the moment of sharing rather than read
 * later by a listener that might run after the filename... well, filenames
 * are immutable once uploaded, but the pattern is kept identical to the note
 * event so both modules stay structurally the same shape, and so a future
 * rename capability (should one ever exist) doesn't require rediscovering
 * this snapshot reasoning.
 *
 * <p>permission travels for the same forward-looking reason NoteSharedEvent
 * carries it: no current consumer needs it, but adding it to the event later
 * means revisiting every publisher.
 */
public record VoiceNoteSharedEvent(
        Long shareId,
        Long voiceNoteId,
        String voiceNoteTitle,
        Long ownerId,
        Long sharedWithUserId,
        SharePermission permission
) {
}

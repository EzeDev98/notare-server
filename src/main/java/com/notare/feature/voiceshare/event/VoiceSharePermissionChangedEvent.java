package com.notare.feature.voiceshare.event;

import com.notare.feature.share.enums.SharePermission;

/**
 * A recipient's access level on a shared voice note changed. Notify the
 * RECIPIENT.
 *
 * <p>Mirrors {@link com.notare.feature.share.event.NoteSharePermissionChangedEvent}.
 * Only published when the level ACTUALLY changed — VoiceShareServiceImpl's
 * updatePermission treats a same-level request as an idempotent no-op and
 * publishes nothing, same as the note-share service.
 *
 * <p>Both levels travel, because "changed" alone doesn't say whether it was a
 * promotion or a demotion, and those mean different things to the recipient.
 */
public record VoiceSharePermissionChangedEvent(
        Long shareId,
        Long voiceNoteId,
        String voiceNoteTitle,
        Long ownerId,
        Long sharedWithUserId,
        SharePermission previousPermission,
        SharePermission newPermission
) {
}

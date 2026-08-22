package com.notare.feature.share.event;

import com.notare.feature.share.enums.SharePermission;

/**
 * A note was shared with someone. Notify the RECIPIENT.
 *
 * <p>Note that the LABEL travels with the event. This isn't convenience — it's
 * correctness. The notification stores a snapshot of the label as it was at
 * this moment (see the Notification entity), and a listener running after the
 * transaction commits could read a label that has since changed. The only place
 * the label is reliably correct is here, inside the operation that used it.
 *
 * <p>The permission is included so the event fully describes the grant, even
 * though the in-app notification currently has nowhere to put it. Future
 * consumers — an email channel that says "you can now edit", the Step 10 audit
 * log — will want it, and adding a field to an event later means revisiting
 * every publisher.
 */
public record NoteSharedEvent(
        Long shareId,
        Long noteId,
        String noteLabel,
        Long ownerId,
        Long sharedWithUserId,
        SharePermission permission
) {
}
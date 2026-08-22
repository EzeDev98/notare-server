package com.notare.feature.share.event;

import com.notare.feature.share.enums.SharePermission;

/**
 * A recipient's access level changed. Notify the RECIPIENT.
 *
 * <p>Only published when the level ACTUALLY changed — setting READ on a grant
 * that is already READ is an idempotent no-op in the service, and firing an
 * event there would produce a notification about nothing.
 *
 * <p>Both the old and new levels are carried, because "changed" is a poor fact
 * on its own: a promotion and a demotion mean very different things to the
 * person receiving them, and only the pair distinguishes the two.
 */
public record NoteSharePermissionChangedEvent(
        Long shareId,
        Long noteId,
        String noteLabel,
        Long ownerId,
        Long sharedWithUserId,
        SharePermission previousPermission,
        SharePermission newPermission
) {
}
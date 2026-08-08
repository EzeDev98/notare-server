package com.notare.feature.share.event;

/**
 * Access to a shared note was withdrawn. Notify the RECIPIENT.
 *
 * <p>The title is captured before the grant is deleted, for the same
 * snapshot reason as NoteSharedEvent — and here it's the ONLY way the recipient
 * can be told which note they lost, since by the time they read the
 * notification they can no longer fetch it.
 *
 * <p>Distinct from CollaborationRemovedEvent (7F): that one ends a
 * relationship and cascades grant deletion; this one is a single grant being
 * withdrawn by hand.
 */
public record NoteShareRevokedEvent(
        Long noteId,
        String noteTitle,
        Long ownerId,
        Long sharedWithUserId
) {
}
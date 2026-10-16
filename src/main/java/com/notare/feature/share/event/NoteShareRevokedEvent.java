package com.notare.feature.share.event;

/**
 * Access to a shared note was withdrawn. Notify the RECIPIENT.
 *
 * <p>The label is captured before the grant is deleted, for the same
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
        String noteLabel,
        Long ownerId,
        Long sharedWithUserId,

        /** True when the recipient left by themselves (step 17f). They are not notified of their own act. */
        boolean left
) {

    public NoteShareRevokedEvent(Long noteId, String noteLabel, Long ownerId, Long sharedWithUserId) {
        this(noteId, noteLabel, ownerId, sharedWithUserId, false);
    }
}
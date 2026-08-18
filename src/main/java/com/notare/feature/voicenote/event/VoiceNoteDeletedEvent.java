package com.notare.feature.voicenote.event;

/**
 * A voice note was permanently deleted. Published for the voiceshare module
 * to consume (Step 6c), so grants on a now-nonexistent voice note don't
 * outlive it as orphan rows.
 *
 * <p>Lives in THIS module (voicenote), not voiceshare — the voicenote module
 * doesn't know voiceshare exists, and shouldn't have to. It just announces a
 * fact about itself; who's listening is not its concern. Same direction rule
 * as CollaborationRemovedEvent living in the collaboration module rather than
 * in share.
 *
 * <p>voiceNoteTitle carries originalFilename, captured BEFORE the row is
 * deleted — same snapshot reasoning as every other share/voiceshare event:
 * once the row is gone, this is the only record of what it was called.
 *
 * <p>No ownerId is included. Unlike VoiceShareRevokedEvent (which is
 * addressed to one specific recipient and needs to say who granted it),
 * cleanup here doesn't need the owner — VoiceShareCleanupListener (Step 6c)
 * only needs the voiceNoteId to delete every grant that points at it,
 * regardless of who granted them.
 *
 * <p>Published from a method that is deliberately NOT @Transactional (see
 * VoiceNoteServiceImpl#deleteVoiceNote) — there is no enclosing transaction
 * for a listener to key off with the ordinary BEFORE_COMMIT phase. Consumers
 * of this specific event use fallbackExecution = true rather than the
 * note-share listener's BEFORE_COMMIT pattern, and that distinction is
 * intentional, not an oversight — copying the note pattern here would mean
 * the listener silently never runs.
 */
public record VoiceNoteDeletedEvent(
        Long voiceNoteId,
        String voiceNoteTitle,
        Long ownerId
) {
}

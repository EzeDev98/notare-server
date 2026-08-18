package com.notare.feature.voiceshare.dto.response;

import com.notare.feature.share.enums.SharePermission;

import java.time.LocalDateTime;

/**
 * A row in "voice notes shared with me": the voice note's metadata, plus what
 * I'm allowed to do with it and who gave it to me.
 *
 * <p>FLATTENED, unlike {@link com.notare.feature.share.dto.response.SharedNoteSummaryResponse}
 * which nests a NoteSummaryResponse. Deliberate divergence, not an
 * inconsistency: a voice note's metadata is already flat and small (no
 * content-preview/truncation logic living in VoiceNoteMapper the way it does
 * for notes), so nesting a VoiceNoteResponse here would add a level of JSON
 * structure with nothing underneath to justify it. If VoiceNoteResponse ever
 * grows real per-field logic worth centralizing, this can be revisited.
 *
 * <p>storageKey is excluded, same reasoning as VoiceNoteResponse: it's an
 * internal detail of where bytes live, never exposed to clients. Recipients
 * fetch audio through the grant-gated stream/download endpoints (Step 5d),
 * never a raw key.
 */
public record SharedVoiceNoteSummaryResponse(

        /** The grant's id, so the client can address it if it ever needs to. */
        Long shareId,

        /** The voice note being shared. */
        Long voiceNoteId,

        /**
         * The optional parent text note.
         *
         * <p>Null means this is a standalone voice note. When present,
         * the recording belongs to this text note and should appear with
         * that note in the recipient's shared-note view.
         */
        Long noteId,

        /** Who owns it — the person who shared it with me. */
        Long ownerId,

        /** What I may do with it. READ-only today (Step 5d gates audio access on READ), kept as the full enum so WRITE has somewhere to land later. */
        SharePermission permission,

        String originalFilename,

        String contentType,

        Long sizeBytes,

        Integer durationSeconds,

        /** When the voice note was originally uploaded. */
        LocalDateTime createdAt,

        /** When it was shared with me. Distinct from createdAt above. */
        LocalDateTime sharedAt
) {
}

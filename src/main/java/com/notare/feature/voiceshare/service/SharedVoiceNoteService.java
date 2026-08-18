package com.notare.feature.voiceshare.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voiceshare.dto.response.SharedVoiceNoteSummaryResponse;
import org.springframework.data.domain.Pageable;

/**
 * The RECIPIENT's side of voice-note sharing: what I can see and hear from
 * voice notes other people have shared with me.
 *
 * <p>Mirrors {@link com.notare.feature.share.service.SharedNoteService}
 * exactly, including the same reason it's not merged with the owner-side
 * service: this asks "do I hold a grant, and is it strong enough?", never
 * "is this mine?". Every method takes the acting user's id and resolves a
 * grant before touching a voice note.
 *
 * <p>No write methods — unlike shared NOTES (which support WRITE via
 * updateSharedNote/autoSaveSharedNote), voice notes have no recipient-side
 * mutation today. VoiceShare.permission still carries the full READ/WRITE
 * range (see that class's doc comment) so a future write capability has
 * somewhere to land without a schema change.
 */
public interface SharedVoiceNoteService {

    /** Voice notes other people have shared with me. */
    PagedResponse<SharedVoiceNoteSummaryResponse> listSharedWithMe(Long userId, Pageable pageable);

    /** One shared voice note's metadata in full. Requires at least READ. */
    SharedVoiceNoteSummaryResponse getSharedVoiceNote(Long userId, Long voiceNoteId);

    /** The shared voice note's audio bytes, for stream/download. Requires at least READ. */
    VoiceNoteContent getAudio(Long userId, Long voiceNoteId);
}

package com.notare.feature.voicenote.dto.response;

/**
 * The outcome of a voice clip upload: the clip, and whether this call stored it. {@code created} is
 * false when the same client id had already uploaded it, so the controller can answer 200, not 201.
 */
public record VoiceUpload(VoiceNoteResponse clip, boolean created) {
}

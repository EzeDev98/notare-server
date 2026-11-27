package com.notare.feature.voicenote.dto.response;

/** The result of a voice clip upload, and whether this call stored it. */
public record VoiceUpload(VoiceNoteResponse clip, boolean created) {
}

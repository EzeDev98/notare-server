package com.notare.feature.voicenote.dto.request;

import org.springframework.core.io.Resource;

/** A voice clip's audio bytes with what is needed to serve them. */
public record VoiceNoteContent(
        Resource resource,
        String contentType,
        String originalFilename,
        long sizeBytes
) {
}

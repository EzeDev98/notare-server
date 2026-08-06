package com.notare.feature.voicenote.dto.request;

import org.springframework.core.io.Resource;

/**
 * Internal carrier for a voice note's bytes plus the metadata needed to serve
 * them: contentType (response media type), originalFilename (download name), and
 * sizeBytes (for HTTP Range math). NOT a web DTO — it holds a Spring Resource,
 * so it's never serialized to JSON; it just travels from service to controller.
 */
public record VoiceNoteContent(
        Resource resource,
        String contentType,
        String originalFilename,
        long sizeBytes
) {
}
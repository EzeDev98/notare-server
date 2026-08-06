package com.notare.feature.voicenote.validation;

import com.notare.common.exception.InvalidVoiceUploadException;
import com.notare.common.exception.UnsupportedAudioFormatException;
import com.notare.common.exception.VoiceFileTooLargeException;
import com.notare.feature.voicenote.config.VoiceNoteProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates an incoming voice upload against the configured rules BEFORE we
 * touch storage or the database. Each failure throws a specific exception that
 * the GlobalExceptionHandler maps to the right status (415 / 413 / 400).
 */
@Component
@RequiredArgsConstructor
public class VoiceUploadValidator {

    private final VoiceNoteProperties properties;

    public void validate(MultipartFile file, Integer durationSeconds) {
        if (file == null || file.isEmpty()) {
            throw new InvalidVoiceUploadException("Audio file is missing or empty.");
        }

        // NOTE: content type here is the client-declared MIME type and is
        // spoofable. For MVP that's acceptable; a hardening step would sniff the
        // file's magic bytes to confirm it's really the claimed format.
        String contentType = file.getContentType();
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType.toLowerCase())) {
            throw new UnsupportedAudioFormatException("Unsupported audio format: " + contentType + ". Allowed: " + String.join(", ", properties.getAllowedContentTypes()) + ".");
        }

        // file.getSize() is the number of bytes actually received (Spring counts
        // it), not a client-declared value, so it's safe to trust here.
        long maxBytes = properties.getMaxSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new VoiceFileTooLargeException("Audio file exceeds the maximum size of " + properties.getMaxSize().toMegabytes() + " MB.");
        }

        if (durationSeconds != null && durationSeconds > properties.getMaxDurationSeconds()) {
            throw new InvalidVoiceUploadException("Audio exceeds the maximum duration of " + properties.getMaxDurationSeconds() + " seconds.");
        }
    }
}
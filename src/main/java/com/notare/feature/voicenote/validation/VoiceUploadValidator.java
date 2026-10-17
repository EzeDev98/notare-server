package com.notare.feature.voicenote.validation;

import com.notare.common.exception.InvalidVoiceUploadException;
import com.notare.common.exception.UnsupportedAudioFormatException;
import com.notare.common.exception.VoiceFileTooLargeException;
import com.notare.feature.voicenote.config.VoiceNoteProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates an incoming voice upload against the configured rules BEFORE we
 * touch storage or the database. Each failure throws a specific exception that
 * the GlobalExceptionHandler maps to the right status (415 / 413 / 400).
 */
@Component
@RequiredArgsConstructor
public class VoiceUploadValidator {

    private static final int HEADER_BYTES = 12;

    private final VoiceNoteProperties properties;

    public void validate(MultipartFile file, Integer durationSeconds) {
        if (file == null || file.isEmpty()) {
            throw new InvalidVoiceUploadException("Audio file is missing or empty.");
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

        // The format is decided by what the file is, never by what the client says it is (step 17j, B-22).
        if (!isSupportedAudio(readHeader(file))) {
            throw new UnsupportedAudioFormatException("Unsupported audio format. Allowed: WebM, Ogg, MP4/M4A, WAV and MP3.");
        }
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_BYTES);
        } catch (IOException ex) {
            throw new InvalidVoiceUploadException("Audio file could not be read.");
        }
    }

    /** WebM, Ogg, MP4/M4A, WAV and MP3, recognised by their first bytes. */
    private static boolean isSupportedAudio(byte[] h) {
        return at(h, 0, 0x1A, 0x45, 0xDF, 0xA3)                                  // WebM (Matroska)
                || at(h, 0, 'O', 'g', 'g', 'S')                                  // Ogg
                || at(h, 4, 'f', 't', 'y', 'p')                                  // MP4 / M4A
                || (at(h, 0, 'R', 'I', 'F', 'F') && at(h, 8, 'W', 'A', 'V', 'E')) // WAV
                || at(h, 0, 'I', 'D', '3')                                       // MP3 with a tag
                || at(h, 0, 0xFF, 0xFB);                                         // MP3 frame
    }

    private static boolean at(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
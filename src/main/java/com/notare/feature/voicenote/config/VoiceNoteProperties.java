package com.notare.feature.voicenote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * App-level validation rules for voice uploads. Registered @Component +
 * @ConfigurationProperties to match the codebase convention. maxSize uses
 * Spring's DataSize so the property can be written as "20MB" instead of a raw
 * byte count.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.voice")
public class VoiceNoteProperties {

    /**
     * MIME types accepted on upload.
     *
     * <p>NOTE: setting notare.voice.allowed-content-types in a .properties file
     * REPLACES this list entirely — Spring binds the bound value over the field
     * initializer rather than merging with it. Keep the two in step, or changing
     * one and not the other will look like it did nothing.
     *
     * <p>The m4a entries are here because .m4a is an MP4 container with AAC
     * audio and clients disagree about what to call it: macOS and curl say
     * audio/x-m4a, Chrome on Windows says audio/mp4, some say audio/m4a. Same
     * bytes, different declarations — which is the weakness of allowlisting a
     * client-supplied MIME type at all. See the note in VoiceUploadValidator
     * about sniffing magic bytes instead.
     */
    private List<String> allowedContentTypes = List.of(
            "audio/mpeg", "audio/mp4", "audio/aac", "audio/m4a", "audio/x-m4a",
            "audio/wav", "audio/x-wav", "audio/webm", "audio/ogg"
    );

    /** App-level max size. Must stay <= spring.servlet.multipart.max-file-size. */
    private DataSize maxSize = DataSize.ofMegabytes(20);

    /** Max accepted duration (validated only when the client supplies a duration). */
    private int maxDurationSeconds = 600;
}
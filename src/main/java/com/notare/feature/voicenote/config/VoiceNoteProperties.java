package com.notare.feature.voicenote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;


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

    /** App-level max size. Must stay <= spring.servlet.multipart.max-file-size. */
    private DataSize maxSize = DataSize.ofMegabytes(20);

    /** Max accepted duration (validated only when the client supplies a duration). */
    private int maxDurationSeconds = 600;
}
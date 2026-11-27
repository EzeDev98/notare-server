package com.notare.feature.voicenote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/** Limits for voice clip uploads. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.voice")
public class VoiceNoteProperties {

    private DataSize maxSize = DataSize.ofMegabytes(20);

    private int maxDurationSeconds = 600;
}

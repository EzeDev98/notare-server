package com.notare.feature.transcription.engine.impl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for Deepgram. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.transcription.deepgram")
public class DeepgramProperties {

    private String apiKey;
    private String baseUrl = "https://api.deepgram.com";
    private String model = "nova-3";

    private boolean mipOptOut = true;

    private int readTimeoutSeconds = 120;
}

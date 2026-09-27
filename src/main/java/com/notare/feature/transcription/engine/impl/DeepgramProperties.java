package com.notare.feature.transcription.engine.impl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.transcription.deepgram")
public class DeepgramProperties {

    private String apiKey;
    private String baseUrl = "https://api.deepgram.com";
    private String model = "nova-3";

    /** Opt out of Deepgram's model-improvement programme (your audio is not used for training). */
    private boolean mipOptOut = true;

    private int readTimeoutSeconds = 120;
}

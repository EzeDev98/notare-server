package com.notare.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed binding for "notare.storage.*". Registered with @Component +
 * @ConfigurationProperties to match how this codebase wires its other property
 * classes (OtpProperties, SessionProperties) — so no separate @EnableConfigurationProperties
 * config class is needed.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.storage")
public class StorageProperties {

    private final Local local = new Local();

    @Getter
    @Setter
    public static class Local {
        /** Base directory under which locally-stored audio files are written. */
        private String location = "./uploads/voice-notes";
    }
}
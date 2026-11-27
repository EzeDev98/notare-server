package com.notare.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings for file storage. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.storage")
public class StorageProperties {

    private String backend = "r2";

    private final Local local = new Local();
    private final R2 r2 = new R2();

    @Getter
    @Setter
    public static class Local {
        private String location = "./uploads/voice-notes";
    }

    @Getter
    @Setter
    public static class R2 {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String secretAccessKey;
        private String region = "auto";
    }
}

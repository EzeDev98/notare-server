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

    /**
     * Which FileStorageService bean is active under @Profile("prod"):
     * "r2" (default, R2FileStorageService) or "postgres"
     * (PostgresFileStorageService, a temporary stand-in until R2 is set up).
     * Dev/test are unaffected — they always get LocalFileStorageService via
     * @Profile("!prod"), regardless of this value.
     */
    private String backend = "r2";

    private final Local local = new Local();
    private final R2 r2 = new R2();

    @Getter
    @Setter
    public static class Local {
        /** Base directory under which locally-stored audio files are written. */
        private String location = "./uploads/voice-notes";
    }

    /**
     * Cloudflare R2 config — only read by {@link R2FileStorageService}, i.e.
     * only when notare.storage.backend=r2 under the prod profile. No hard
     * requirement on these at the Java level; application-prod.properties
     * gives them empty-string defaults so the app can still boot when
     * backend=postgres. When backend=r2 IS selected, R2FileStorageService's
     * own constructor is what fails fast on a blank endpoint/bucket/keys —
     * same reasoning app.jwt.secret already documents for itself.
     */
    @Getter
    @Setter
    public static class R2 {
        /** e.g. https://<ACCOUNT_ID>.r2.cloudflarestorage.com — copied verbatim
         *  from the R2 dashboard, not assembled from a bare account ID. */
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String secretAccessKey;
    }
}